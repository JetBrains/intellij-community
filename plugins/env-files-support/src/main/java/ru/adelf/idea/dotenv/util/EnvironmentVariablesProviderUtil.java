package ru.adelf.idea.dotenv.util;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesProvider;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesUsagesProvider;

public class EnvironmentVariablesProviderUtil {
    public static final EnvironmentVariablesProvider[] PROVIDERS = getEnvVariablesProviders();

    public static final EnvironmentVariablesUsagesProvider[] USAGES_PROVIDERS = getEnvVariablesUsagesProviders();

    private static EnvironmentVariablesProvider[] getEnvVariablesProviders() {
        return getExtensions("ru.adelf.idea.dotenv.environmentVariablesProvider");
    }

    private static EnvironmentVariablesUsagesProvider[] getEnvVariablesUsagesProviders() {
        return getExtensions("ru.adelf.idea.dotenv.environmentVariablesUsagesProvider");
    }

    private static <T> T[] getExtensions(@NotNull String name) {
        ExtensionPointName<T> pointName = new ExtensionPointName<>(name);

        return pointName.getExtensions();
    }
}
