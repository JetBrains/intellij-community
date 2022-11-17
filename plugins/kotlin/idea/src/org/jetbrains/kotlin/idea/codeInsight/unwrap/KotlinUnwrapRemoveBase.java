// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.unwrap;

import com.intellij.codeInsight.unwrap.AbstractUnwrapper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle;
import org.jetbrains.kotlin.idea.refactoring.ElementSelectionUtilsKt;
import org.jetbrains.kotlin.psi.KtBlockExpression;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.kotlin.psi.KtExpression;

import java.util.List;

public abstract class KotlinUnwrapRemoveBase extends AbstractUnwrapper<KotlinUnwrapRemoveBase.Context> {
    private final String key;

    protected KotlinUnwrapRemoveBase(@NotNull String key) {
        /*
        Pass empty description to superclass since actual description
        is computed based on the Psi element at hand
        */
        super("");
        this.key = key;
    }

    @Override
    public @NotNull String getDescription(@NotNull PsiElement e) {
        assert e instanceof KtElement;
        return KotlinBundle.message(key, ElementSelectionUtilsKt.getExpressionShortText((KtElement) e));
    }

    protected boolean canExtractExpression(@NotNull KtExpression expression, @NotNull KtElement parent) {
        if (expression instanceof KtBlockExpression) {
            KtBlockExpression block = (KtBlockExpression) expression;

            return block.getStatements().size() <= 1 || parent instanceof KtBlockExpression;
        }
        return true;
    }

    protected static class Context extends AbstractUnwrapper.AbstractContext {
        @Override
        protected boolean isWhiteSpace(PsiElement element) {
            return element instanceof PsiWhiteSpace;
        }

        public void extractFromBlock(@NotNull KtBlockExpression block, @NotNull KtElement from) throws IncorrectOperationException {
            List<KtExpression> expressions = block.getStatements();
            if (!expressions.isEmpty()) {
                extract(expressions.get(0), expressions.get(expressions.size() - 1), from);
            }
        }

        public void extractFromExpression(@NotNull KtExpression expression, @NotNull KtElement from) throws IncorrectOperationException {
            if (expression instanceof KtBlockExpression) {
                extractFromBlock((KtBlockExpression) expression, from);
            }
            else {
                extract(expression, expression, from);
            }
        }

        public void replace(@NotNull KtElement originalElement, @NotNull KtElement newElement) {
            if (myIsEffective) {
                originalElement.replace(newElement);
            }
        }
    }

    @Override
    protected Context createContext() {
        return new Context();
    }
}
