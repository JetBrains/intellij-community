package ru.adelf.idea.dotenv.ruby;

import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.stringLiterals.RStringLiteral;
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RArrayIndexing;
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.RConstant;
import ru.adelf.idea.dotenv.extension.DotEnvReference;

import java.util.Objects;

public class RubyEnvReferenceContributor extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(RStringLiteral.class),
                new PsiReferenceProvider() {
                    @NotNull
                    @Override
                    public PsiReference[] getReferencesByElement(@NotNull PsiElement element,
                                                                 @NotNull ProcessingContext context) {
                        RStringLiteral literal = (RStringLiteral) element;

                        if (literal.getParent() == null) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        PsiElement array = literal.getParent().getParent();

                        if (!(array instanceof RArrayIndexing)) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        PsiElement receiver = ((RArrayIndexing) array).getReceiver();

                        if (!(receiver instanceof RConstant)) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        if (receiver.getFirstChild() == null) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        if (!Objects.equals(receiver.getFirstChild().getText(), "ENV")) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        return new PsiReference[]{new DotEnvReference(literal, new TextRange(1, literal.getTextLength() + 1))};
                    }
                });
    }
}