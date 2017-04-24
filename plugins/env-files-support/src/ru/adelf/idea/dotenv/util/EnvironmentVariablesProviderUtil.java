package ru.adelf.idea.dotenv.util;

import ru.adelf.idea.dotenv.DotEnvVariablesProvider;
import ru.adelf.idea.dotenv.api.EnvVariablesProvider;
import ru.adelf.idea.dotenv.docker.DockerfileVariablesProvider;

import java.util.HashSet;
import java.util.Set;

public class EnvironmentVariablesProviderUtil {
    public static final Set<EnvVariablesProvider> PROVIDERS = getEnvVariablesProviders();

    private static Set<EnvVariablesProvider> getEnvVariablesProviders() {
        Set<EnvVariablesProvider> providers = new HashSet<>();

        providers.add(new DotEnvVariablesProvider());
        providers.add(new DockerfileVariablesProvider());

        return providers;
    }
}
