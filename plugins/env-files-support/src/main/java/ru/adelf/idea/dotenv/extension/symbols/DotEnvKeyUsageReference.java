package ru.adelf.idea.dotenv.extension.symbols;

import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesApi;
import ru.adelf.idea.dotenv.psi.DotEnvKey;

import java.util.Arrays;
import java.util.Collection;

@SuppressWarnings("UnstableApiUsage")
class DotEnvKeyUsageReference implements PsiSymbolReference {
    private final DotEnvKey keyElement;

    DotEnvKeyUsageReference(DotEnvKey keyElement) {
        this.keyElement = keyElement;
    }

    @Override
    public @NotNull PsiElement getElement() {
        return keyElement;
    }

    @Override
    public @NotNull TextRange getRangeInElement() {
        return new TextRange(0, keyElement.getTextLength());
    }

    @Override
    public @NotNull Collection<? extends Symbol> resolveReference() {
        return Arrays.stream(EnvironmentVariablesApi.getKeyUsages(keyElement.getProject(), keyElement.getText()))
                .map(DotEnvKeyUsageSymbol::new)
                .toList();
    }
}
