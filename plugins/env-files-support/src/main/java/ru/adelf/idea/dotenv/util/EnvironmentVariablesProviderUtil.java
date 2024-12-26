package ru.adelf.idea.dotenv.util;

import com.intellij.openapi.extensions.ExtensionPointName;
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
}
