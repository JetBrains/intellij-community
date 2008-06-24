/*
 * Copyright 2007-2008 Bas Leijdekkers
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
        if (!(element instanceof PsiExpressionList)) {
            return false;
        }
        final PsiExpressionList argumentList = (PsiExpressionList) element;
        final PsiElement grandParent = argumentList.getParent();
        if (!(grandParent instanceof PsiMethodCallExpression)) {
            return false;
        }
        final PsiMethodCallExpression methodCallExpression =
                (PsiMethodCallExpression) grandParent;
        final PsiMethod method = methodCallExpression.resolveMethod();
        if (method == null || !method.isVarArgs()) {
            return false;
        }
        final PsiParameterList parameterList = method.getParameterList();
        final int parametersCount = parameterList.getParametersCount();
        final PsiExpression[] arguments = argumentList.getExpressions();
        if (arguments.length < parametersCount) {
            return false;
        }
        if (arguments.length != parametersCount) {
            return true;
        }
        final PsiExpression lastExpression =
                arguments[arguments.length - 1];
        final PsiType lastArgumentType = lastExpression.getType();
        if (!(lastArgumentType instanceof PsiArrayType)) {
            return true;
        }
        final PsiArrayType arrayType = (PsiArrayType)lastArgumentType;
        final PsiType type = arrayType.getComponentType();
        final PsiParameter[] parameters = parameterList.getParameters();
        final PsiParameter lastParameter = parameters[parameters.length - 1];
        final PsiEllipsisType lastParameterType =
                (PsiEllipsisType)lastParameter.getType();
        final PsiType lastType = lastParameterType.getComponentType();
        final JavaResolveResult resolveResult =
                methodCallExpression.resolveMethodGenerics();
        final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
        final PsiType substitutedType = substitutor.substitute(lastType);
        return !substitutedType.equals(type);
    }
}