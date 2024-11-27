package ru.adelf.idea.dotenv.java;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class JavaEnvironmentClasses {
    public static boolean isDirectMethodCall(String methodName) {
        return methodName.equals("getenv") || methodName.equals("getEnv");
    }

    @Nullable
    public static List<String> getClassNames(String methodName) {
        return switch (methodName) {
            case "get" -> Arrays.asList("Dotenv", "DotEnv");
            case "getProperty" -> Collections.singletonList("System");
            default -> null;
        };
    }
}
