/*
 * Copyright 2007 Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ipp.varargs;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

class VarargArgumentsPredicate implements PsiElementPredicate {

    public boolean satisfiedBy(@NotNull PsiElement element) {
        if (!(element instanceof PsiExpression)) {
            return false;
        }
        final PsiExpression expression = (PsiExpression) element;
        final PsiElement parent = expression.getParent();
        if (!(parent instanceof PsiExpressionList)) {
            return false;
        }
        final PsiExpressionList expressionList = (PsiExpressionList) parent;
        final PsiElement grandParent = expressionList.getParent();
        if (!(grandParent instanceof PsiMethodCallExpression)) {
            return false;
        }
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) grandParent;
        final PsiMethod method = methodCallExpression.resolveMethod();
        if (method == null || !method.isVarArgs()) {
            return false;
        }
        final int index = indexOfExpressionInExpressionList(expression, expressionList);
        final PsiParameterList parameterList = method.getParameterList();
        final int parametersCount = parameterList.getParametersCount();
        return index == parametersCount - 1;
    }

    private static int indexOfExpressionInExpressionList(
            @NotNull PsiExpression expression, @NotNull PsiExpressionList expressionList) {
        final PsiExpression[] expressions = expressionList.getExpressions();
        for (int i = 0; i < expressions.length; i++) {
            final PsiExpression listExpression = expressions[i];
            if (expression.equals(listExpression)) {
                return i;
            }
        }
        return -1;
    }
}