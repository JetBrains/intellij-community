package ru.adelf.idea.dotenv.models;

import org.jetbrains.annotations.NotNull;

/**
 * Environment key and value
 */
public class EnvironmentKeyValue {

    private final String key;
    private final String value;

    public EnvironmentKeyValue(@NotNull String key, @NotNull String value) {
        this.key = key;
        this.value = value;
    }

    public @NotNull String getKey() {
        return key;
    }

    public @NotNull String getValue() {
        return value;
    }
}
