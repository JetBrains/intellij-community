// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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

    @Nullable
    protected abstract KtExpression getExpressionToUnwrap(@NotNull KtElement target);

    @NotNull
    protected KtElement getEnclosingElement(@NotNull KtElement element) {
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
