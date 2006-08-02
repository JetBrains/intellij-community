/*
 * Copyright 2006 Bas Leijdekkers
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
package com.siyeh.ipp.commutative;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class SwapMethodCallArgumentsPredicate implements PsiElementPredicate {

    public boolean satisfiedBy(@NotNull PsiElement element) {
        if (!(element instanceof PsiExpressionList)) {
            return false;
        }
        final PsiExpressionList argumentList = (PsiExpressionList)element;
        final PsiExpression[] arguments = argumentList.getExpressions();
        if (arguments.length != 2) {
            return false;
        }
        final PsiElement parent = argumentList.getParent();
        if (!(parent instanceof PsiCallExpression)) {
            return false;
        }
        final PsiCallExpression methodCallExpression = (PsiCallExpression)parent;
        final PsiMethod method = methodCallExpression.resolveMethod();
        if (method == null) {
            return false;
        }
        final PsiParameterList parameterList = method.getParameterList();
        final PsiParameter[] parameters = parameterList.getParameters();
        if (parameters.length != 2) {
            return false;
        }
        final PsiParameter firstParameter = parameters[0];
        final PsiParameter secondParameter = parameters[1];
        final PsiType firstType = firstParameter.getType();
        final PsiType secondType = secondParameter.getType();
        return firstType.equals(secondType);
    }
}
