package ru.adelf.idea.dotenv.php;

import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.extension.DotEnvReference;

public class PhpEnvReferenceContributor extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(StringLiteralExpression.class),
                new PsiReferenceProvider() {
                    @NotNull
                    @Override
                    public PsiReference[] getReferencesByElement(@NotNull PsiElement element,
                                                                 @NotNull ProcessingContext context) {
                        StringLiteralExpression literal = (StringLiteralExpression) element;

                        if (!PhpPsiHelper.isEnvFunctionParameter(literal)) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        String value = literal.getContents();

                        return new PsiReference[]{new DotEnvReference(element, new TextRange(1, value.length() + 1))};
                    }
                });
    }
}