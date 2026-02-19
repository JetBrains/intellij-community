package ru.adelf.idea.dotenv.util;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.models.EnvironmentKeyValue;
import ru.adelf.idea.dotenv.models.KeyUsagePsiElement;
import ru.adelf.idea.dotenv.models.KeyValuePsiElement;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public final class EnvironmentVariablesUtil {
    public static @NotNull EnvironmentKeyValue getKeyValueFromString(@NotNull String s) {
        int pos = s.indexOf("=");

        if(pos == -1) {
            return new EnvironmentKeyValue(s.trim(), "");
        } else {
            return new EnvironmentKeyValue(s.substring(0, pos).trim(), s.substring(pos + 1).trim());
        }
    }

    public static @NotNull String getKeyFromString(@NotNull String s) {
        int pos = s.indexOf("=");

        if(pos == -1) {
            return s.trim();
        } else {
            return s.substring(0, pos).trim();
        }
    }

    public static @NotNull Set<PsiElement> getElementsByKey(String key, Collection<KeyValuePsiElement> items) {
        return items.stream().filter(item -> item.getKey().equals(key)).map(KeyValuePsiElement::getElement).collect(Collectors.toSet());
    }

    public static @NotNull Set<PsiElement> getUsagesElementsByKey(String key, Collection<KeyUsagePsiElement> items) {
        return items.stream().filter(item -> item.getKey().equals(key)).map(KeyUsagePsiElement::getElement).collect(Collectors.toSet());
    }
}
