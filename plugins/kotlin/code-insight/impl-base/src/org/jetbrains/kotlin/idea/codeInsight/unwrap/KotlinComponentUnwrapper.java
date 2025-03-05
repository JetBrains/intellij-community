// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.unwrap;

import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.kotlin.psi.KtExpression;

public abstract class KotlinComponentUnwrapper extends KotlinUnwrapRemoveBase {
    public KotlinComponentUnwrapper(String key) {
        super(key);
    }

    protected abstract @Nullable KtExpression getExpressionToUnwrap(@NotNull KtElement target);

    protected @NotNull KtElement getEnclosingElement(@NotNull KtElement element) {
        return element;
    }

    @Override
    public boolean isApplicableTo(PsiElement e) {
        if (!(e instanceof KtElement)) return false;

        KtExpression expressionToUnwrap = getExpressionToUnwrap((KtElement) e);
        return expressionToUnwrap != null && canExtractExpression(expressionToUnwrap,
                                                                  (KtElement) getEnclosingElement((KtElement) e).getParent());
    }

    @Override
    protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
        KtElement targetElement = (KtElement) element;
        KtExpression expressionToUnwrap = getExpressionToUnwrap(targetElement);
        assert expressionToUnwrap != null;

        KtElement enclosingElement = getEnclosingElement(targetElement);
        context.extractFromExpression(expressionToUnwrap, enclosingElement);
        context.delete(enclosingElement);
    }
}
