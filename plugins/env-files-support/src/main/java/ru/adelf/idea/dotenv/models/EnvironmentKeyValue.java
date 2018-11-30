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

    @NotNull
    public String getKey() {
        return key;
    }

    @NotNull
    public String getValue() {
        return value;
    }
}
