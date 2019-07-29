package ru.adelf.idea.dotenv.js;

import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.extension.DotEnvReference;

public class JsEnvReferenceContributor extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(JSReferenceExpression.class),
                new PsiReferenceProvider() {
                    @NotNull
                    @Override
                    public PsiReference[] getReferencesByElement(@NotNull PsiElement element,
                                                                 @NotNull ProcessingContext context) {
                        JSReferenceExpression reference = (JSReferenceExpression) element;

                        if (!reference.getCanonicalText().startsWith("process.env.")) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        String value = reference.getCanonicalText();

                        return new PsiReference[]{new DotEnvReference(element, new TextRange(0, value.length()), value.substring(12))};
                    }
                });
    }
}