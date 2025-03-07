package com.frannzg.iapgoogle.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.frannzg.iapgoogle.R;
import com.frannzg.iapgoogle.databinding.FragmentHomeBinding;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment implements View.OnClickListener {

    private FragmentHomeBinding binding;
    private static final String TAG = "HomeFragment";
    private BillingClient billingClient;

    private static final String PRODUCT_ID_INAPP = "producte1"; // ID de producto in-app
    private static final String PRODUCT_ID_SUBSCRIPTION = "producte2"; // ID de suscripción

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        HomeViewModel homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Asignar listeners a los botones
        binding.btnComprar.setOnClickListener(this);
        binding.btnSuscribirse.setOnClickListener(this);

        // Inicializar BillingClient
        billingClient = BillingClient.newBuilder(requireContext())
                .enablePendingPurchases()
                .setListener(
                        (billingResult, purchases) -> {
                            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                                for (Purchase purchase : purchases) {
                                    handlePurchase(purchase);
                                }
                            } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
                                Log.d(TAG, "Usuario canceló la compra");
                            } else {
                                Log.d(TAG, "Purchase error: " + billingResult.getResponseCode() + " - " + billingResult.getDebugMessage());
                            }
                        }
                ).build();
        establishConnection();

        return root;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btnComprar) {
            queryProduct(PRODUCT_ID_INAPP, BillingClient.ProductType.INAPP);
        } else if (v.getId() == R.id.btnSuscribirse) {
            queryProduct(PRODUCT_ID_SUBSCRIPTION, BillingClient.ProductType.SUBS);
        }
    }

    private void queryProduct(String productId, String productType) {
        Log.d(TAG, "Consultando producto: " + productId + " tipo: " + productType);

        // Verificar primero si el servicio está conectado
        if (!billingClient.isReady()) {
            Log.d(TAG, "BillingClient no está listo. Reconectando...");
            establishConnection();
            return;
        }

        List<QueryProductDetailsParams.Product> productList = new ArrayList<>();

        productList.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(productType)
                .build());

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build();

        billingClient.queryProductDetailsAsync(params,
                (billingResult, productDetailsList) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        if (!productDetailsList.isEmpty()) {
                            ProductDetails productDetails = productDetailsList.get(0);
                            Log.d(TAG, "Producto encontrado: " + productDetails.getName());
                            launchPurchaseFlow(productDetails, productType);
                        } else {
                            Log.d(TAG, "No se encontraron detalles del producto: " + productId);
                        }
                    } else {
                        Log.d(TAG, "Fallo en la consulta del producto: " + billingResult.getResponseCode() + " - " + billingResult.getDebugMessage());
                    }
                });
    }

    private void handlePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            Log.d(TAG, "Compra realizada: " + purchase.getProducts().get(0));

            // Verificar si la compra ha sido ya confirmada
            if (!purchase.isAcknowledged()) {
                AcknowledgePurchaseParams acknowledgePurchaseParams =
                        AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.getPurchaseToken())
                                .build();
                billingClient.acknowledgePurchase(acknowledgePurchaseParams,
                        billingResult -> {
                            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                                Log.d(TAG, "Compra confirmada exitosamente");
                                updateUIForPurchase(purchase.getProducts().get(0));
                            } else {
                                Log.d(TAG, "Fallo al confirmar la compra: " + billingResult.getResponseCode() + " - " + billingResult.getDebugMessage());
                            }
                        });
            } else {
                Log.d(TAG, "Compra ya confirmada anteriormente");
                updateUIForPurchase(purchase.getProducts().get(0));
            }
        } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
            Log.d(TAG, "Compra pendiente para: " + purchase.getProducts().get(0));
        }
    }

    private void updateUIForPurchase(String productId) {
        // Actualizar la UI basado en el producto comprado
        if (productId.equals(PRODUCT_ID_INAPP)) {
            // Actualizar UI para compra única
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    binding.btnComprar.setText("Producto Comprado");
                    binding.btnComprar.setEnabled(false);
                });
            }
        } else if (productId.equals(PRODUCT_ID_SUBSCRIPTION)) {
            // Actualizar UI para suscripción
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    binding.btnSuscribirse.setText("Suscripción Activa");
                    binding.btnSuscribirse.setEnabled(false);
                });
            }
        }
    }

    private void establishConnection() {
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Conexión con el servicio de facturación establecida");
                    // Al establecer conexión, verificar compras existentes
                    queryPurchases();
                } else {
                    Log.d(TAG, "Fallo en la conexión con el servicio de facturación: " + billingResult.getResponseCode() + " - " + billingResult.getDebugMessage());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                Log.d(TAG, "Servicio de facturación desconectado");
                // No reconectar automáticamente para evitar bucles, mejor hacerlo bajo demanda
            }
        });
    }

    private void queryPurchases() {
        billingClient.queryPurchasesAsync(
                BillingClient.ProductType.INAPP,
                (billingResult, purchases) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        for (Purchase purchase : purchases) {
                            if (purchase.getProducts().contains(PRODUCT_ID_INAPP)) {
                                Log.d(TAG, "Compra existente encontrada: " + PRODUCT_ID_INAPP);
                                updateUIForPurchase(PRODUCT_ID_INAPP);
                            }
                        }
                    }
                }
        );

        billingClient.queryPurchasesAsync(
                BillingClient.ProductType.SUBS,
                (billingResult, purchases) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        for (Purchase purchase : purchases) {
                            if (purchase.getProducts().contains(PRODUCT_ID_SUBSCRIPTION)) {
                                Log.d(TAG, "Suscripción existente encontrada: " + PRODUCT_ID_SUBSCRIPTION);
                                updateUIForPurchase(PRODUCT_ID_SUBSCRIPTION);
                            }
                        }
                    }
                }
        );
    }

    private void launchPurchaseFlow(ProductDetails productDetails, String productType) {
        List<BillingFlowParams.ProductDetailsParams> productDetailsParamsList = new ArrayList<>();

        if (productType.equals(BillingClient.ProductType.SUBS)) {
            // Para suscripciones, usar offerToken si está disponible
            try {
                // Esto funcionará con versiones recientes de la Billing Library
                // Acceder usando reflexión para compatibilidad con versiones antiguas
                List<?> subscriptionOfferDetails = (List<?>) productDetails.getClass()
                        .getMethod("getSubscriptionOfferDetails")
                        .invoke(productDetails);

                if (subscriptionOfferDetails != null && !subscriptionOfferDetails.isEmpty()) {
                    // Obtenemos el token de la primera oferta
                    Object firstOffer = subscriptionOfferDetails.get(0);
                    String offerToken = (String) firstOffer.getClass()
                            .getMethod("getOfferToken")
                            .invoke(firstOffer);

                    Log.d(TAG, "Usando offerToken para suscripción: " + offerToken);

                    productDetailsParamsList.add(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(productDetails)
                                    .setOfferToken(offerToken)
                                    .build()
                    );
                } else {
                    // Fallback si no hay ofertas disponibles
                    productDetailsParamsList.add(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(productDetails)
                                    .build()
                    );
                }
            } catch (Exception e) {
                // Si falla la reflexión, usar el método estándar
                Log.d(TAG, "Usando método estándar para suscripción (sin offerToken): " + e.getMessage());
                productDetailsParamsList.add(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productDetails)
                                .build()
                );
            }

            Log.d(TAG, "Lanzando flujo de suscripción para: " + productDetails.getProductId());
        } else {
            // Para compras únicas
            productDetailsParamsList.add(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .build()
            );

            Log.d(TAG, "Lanzando flujo de compra para el producto: " + productDetails.getProductId());
        }

        BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build();

        if (isAdded() && getActivity() != null) {
            BillingResult result = billingClient.launchBillingFlow(requireActivity(), billingFlowParams);
            Log.d(TAG, "Resultado del flujo de compra: " + result.getResponseCode() + " - " + result.getDebugMessage());
        } else {
            Log.d(TAG, "No se pudo lanzar el flujo de compra: Fragmento no añadido o actividad nula");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (billingClient != null) {
            billingClient.endConnection();
        }
        binding = null;
    }
}