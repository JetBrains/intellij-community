package ru.adelf.idea.dotenv.util;

import com.intellij.psi.PsiElement;
import javafx.util.Pair;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.models.KeyUsagePsiElement;
import ru.adelf.idea.dotenv.models.KeyValuePsiElement;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

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

    @NotNull
    public static String getValueFromString(@NotNull String s) {
        int pos = s.indexOf("=");

        if(pos == -1) {
            return "";
        } else {
            return s.substring(pos + 1).trim();
        }
    }

    @NotNull
    public static Collection<Pair<String, String>> getKeyValues(Collection<KeyValuePsiElement> items) {
        return items.stream().map(item -> {
            return new Pair<>(item.getKey(), item.getValue());
        }).collect(Collectors.toList());
    }

    @NotNull
    public static Set<PsiElement> getElementsByKey(String key, Collection<KeyValuePsiElement> items) {
        return items.stream().filter(item -> {
            return item.getKey().equals(key);
        }).map(KeyValuePsiElement::getElement).collect(Collectors.toSet());
    }

    @NotNull
    public static Set<PsiElement> getUsagesElementsByKey(String key, Collection<KeyUsagePsiElement> items) {
        return items.stream().filter(item -> {
            return item.getKey().equals(key);
        }).map(KeyUsagePsiElement::getElement).collect(Collectors.toSet());
    }
}
