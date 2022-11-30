// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.unwrap;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.*;

import java.util.List;

public class KotlinLambdaUnwrapper extends KotlinUnwrapRemoveBase {
    public KotlinLambdaUnwrapper(String key) {
        super(key);
    }

    private static KtElement getLambdaEnclosingElement(@NotNull KtLambdaExpression lambda) {
        PsiElement parent = lambda.getParent();

        if (parent instanceof KtValueArgument) {
            KtCallExpression callExpression = PsiTreeUtil.getParentOfType(parent, KtCallExpression.class, true);
            if (callExpression != null) {
                PsiElement parentParent = callExpression.getParent();
                if (parentParent instanceof KtQualifiedExpression) {
                    return (KtElement) parentParent;
                }
            }
            return callExpression;
        }

        if (parent instanceof KtCallExpression) {
            return (KtElement) parent;
        }

        if (parent instanceof KtProperty && ((KtProperty) parent).isLocal()) {
            return (KtElement) parent;
        }

        return lambda;
    }

    @Override
    public boolean isApplicableTo(PsiElement e) {
        if (!(e instanceof KtLambdaExpression)) return false;

        KtLambdaExpression lambda = (KtLambdaExpression) e;
        KtBlockExpression body = lambda.getBodyExpression();
        KtElement enclosingElement = getLambdaEnclosingElement(lambda);

        if (body == null || enclosingElement == null) return false;

        return canExtractExpression(body, (KtElement)enclosingElement.getParent());
    }

    @Override
    protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
        KtLambdaExpression lambda = (KtLambdaExpression) element;
        KtBlockExpression body = lambda.getBodyExpression();
        KtElement enclosingExpression = getLambdaEnclosingElement(lambda);

        context.extractFromBlock(body, enclosingExpression);
        context.delete(enclosingExpression);
    }

    @Override
    public PsiElement collectAffectedElements(@NotNull PsiElement e, @NotNull List<? super PsiElement> toExtract) {
        super.collectAffectedElements(e, toExtract);
        return getLambdaEnclosingElement((KtLambdaExpression) e);
    }
}
