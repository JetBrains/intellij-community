package ru.adelf.idea.dotenv.go;

import com.goide.psi.GoArgumentList;
import com.goide.psi.GoCallExpr;
import com.goide.psi.GoStringLiteral;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.extension.DotEnvReference;

public class GoEnvReferenceContributor extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(GoStringLiteral.class),
                new PsiReferenceProvider() {
                    @NotNull
                    @Override
                    public PsiReference[] getReferencesByElement(@NotNull PsiElement element,
                                                                 @NotNull ProcessingContext context) {
                        GoStringLiteral literal = (GoStringLiteral) element;

                        if(literal.getParent() == null || !(literal.getParent() instanceof GoArgumentList)) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        GoArgumentList argumentList = (GoArgumentList)literal.getParent();

                        if(argumentList.getParent() == null || !(argumentList.getParent() instanceof GoCallExpr)) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        GoStringLiteral envLiteral = GoPsiHelper.getEnvironmentGoLiteral((GoCallExpr) argumentList.getParent());

                        if (envLiteral != literal) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        return new PsiReference[]{new DotEnvReference(literal, new TextRange(1, literal.getTextLength() + 1))};
                    }
                });
    }
}