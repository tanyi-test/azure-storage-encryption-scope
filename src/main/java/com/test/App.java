package com.test;

import com.microsoft.azure.arm.resources.Region;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.keyvault.webkey.JsonWebKeyType;
import com.microsoft.azure.management.keyvault.Key;
import com.microsoft.azure.management.keyvault.Secret;
import com.microsoft.azure.management.keyvault.Vault;
import com.microsoft.azure.management.keyvault.implementation.KeyVaultManager;
import com.microsoft.azure.management.resources.implementation.ResourceManager;
import com.microsoft.azure.management.storage.v2019_06_01.BlobContainer;
import com.microsoft.azure.management.storage.v2019_06_01.EncryptionScope;
import com.microsoft.azure.management.storage.v2019_06_01.EncryptionScopeKeyVaultProperties;
import com.microsoft.azure.management.storage.v2019_06_01.EncryptionScopeState;
import com.microsoft.azure.management.storage.v2019_06_01.Kind;
import com.microsoft.azure.management.storage.v2019_06_01.SkuName;
import com.microsoft.azure.management.storage.v2019_06_01.implementation.SkuInner;
import com.microsoft.azure.management.storage.v2019_06_01.implementation.StorageManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Hello world!
 *
 */
public class App {
    public static String randomString(String prefix, int len) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, len - prefix.length());
    }

    public static void main(String[] args) throws Exception {
        final String resourceGroupName = randomString("rg", 8);
        final String vaultName = randomString("vlt", 8);
        final String vaultKeyName = randomString("k", 8);
        final String storageAccountName = randomString("sg", 8);
        final String encryptScopeName = randomString("es", 8);
        final Region region = Region.US_WEST;

        final File credFile = new File(System.getenv("AZURE_AUTH_LOCATION"));

        ApplicationTokenCredentials credentials = ApplicationTokenCredentials.fromFile(credFile);

        KeyVaultManager keyVaultManager = KeyVaultManager.authenticate(credentials, credentials.defaultSubscriptionId());
        StorageManager storageManager = StorageManager.authenticate(credentials, credentials.defaultSubscriptionId());

        // create
        Vault vault = keyVaultManager.vaults().define(vaultName)
                .withRegion(region.toString())
                .withNewResourceGroup(resourceGroupName)
                .defineAccessPolicy()
                    .forServicePrincipal(credentials.clientId())
                    .allowSecretAllPermissions()
                    .allowCertificateAllPermissions()
                    .allowKeyAllPermissions()
                    .allowStorageAllPermissions()
                    .attach()
                .create();

        Key key = vault.keys().define(vaultKeyName)
                .withKeyTypeToCreate(JsonWebKeyType.RSA)
                .create();

        storageManager.storageAccounts().define(storageAccountName)
                .withRegion(region)
                .withExistingResourceGroup(resourceGroupName)
                .withKind(Kind.STORAGE_V2)
                .withSku(new SkuInner().withName(SkuName.PREMIUM_LRS))
                .create();

        storageManager.encryptionScopes().define(encryptScopeName)
                .withExistingStorageAccount(resourceGroupName, storageAccountName)
                .create();

        // get
        EncryptionScope encryptionScope = storageManager.encryptionScopes().getAsync(resourceGroupName, storageAccountName, encryptScopeName)
                .toBlocking().last();

        System.out.println(encryptionScope.id());

        // list
        for (EncryptionScope scope : storageManager.encryptionScopes().listAsync(resourceGroupName, storageAccountName)
                .toBlocking().toIterable()) {
            System.out.println(scope.id());
        }

        // update
        encryptionScope.update()
                .withKeyVaultProperties(new EncryptionScopeKeyVaultProperties().withKeyUri(key.inner().keyIdentifier().identifier()))
                .withState(EncryptionScopeState.DISABLED) // this could be separated
                .apply();
        System.out.println(encryptionScope.state());
        System.out.println(encryptionScope.keyVaultProperties().keyUri());

        // delete
        storageManager.storageAccounts().deleteByResourceGroup(resourceGroupName, storageAccountName);
        keyVaultManager.resourceManager().resourceGroups().beginDeleteByName(resourceGroupName);
    }
}
