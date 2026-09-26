package ru.adelf.idea.dotenv.util;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.ModificationTracker;
import kotlinx.coroutines.CoroutineScope;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesProvider;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesUsagesProvider;

public final class EnvironmentVariablesProviderUtil {
    private static final ExtensionPointName<EnvironmentVariablesProvider> providersEP
            = new ExtensionPointName<>("ru.adelf.idea.dotenv.environmentVariablesProvider");

    private static final ExtensionPointName<EnvironmentVariablesUsagesProvider> usageProvidersEP
            = new ExtensionPointName<>("ru.adelf.idea.dotenv.environmentVariablesUsagesProvider");

    public static EnvironmentVariablesProvider[] getEnvVariablesProviders() {
        return providersEP.getExtensions();
    }

    public static EnvironmentVariablesUsagesProvider[] getEnvVariablesUsagesProviders() {
        return usageProvidersEP.getExtensions();
    }

    public static ModificationTracker getEnvVariablesUsagesProvidersModificationTracker() {
        return EnvironmentVariablesUsagesProvidersModificationTracker.getInstance();
    }

    static void addEnvVariablesUsagesProvidersChangeListener(CoroutineScope coroutineScope, Runnable listener) {
        usageProvidersEP.addChangeListener(coroutineScope, listener);
    }
}
