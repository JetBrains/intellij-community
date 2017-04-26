package ru.adelf.idea.dotenv.util;

import javafx.util.Pair;
import org.jetbrains.annotations.NotNull;

public class EnvironmentVariablesUtil {
    @NotNull
    public static Pair<String, String> getKeyValueFromString(@NotNull String s) {
        int pos = s.indexOf("=");

        if(pos == -1) {
            return new Pair<>(s.trim(), "");
        } else {
            return new Pair<>(s.substring(0, pos).trim(), s.substring(pos + 1).trim());
        }
    }

    @NotNull
    public static String getKeyFromString(@NotNull String s) {
        int pos = s.indexOf("=");

        if(pos == -1) {
            return s.trim();
        } else {
            return s.substring(0, pos).trim();
        }
    }
}
