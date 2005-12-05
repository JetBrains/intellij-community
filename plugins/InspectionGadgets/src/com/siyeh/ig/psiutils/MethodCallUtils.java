/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

public class MethodCallUtils {

    private MethodCallUtils() {
        super();
    }

    @Nullable
    public static String getMethodName(
            @NotNull PsiMethodCallExpression expression) {
        final PsiReferenceExpression method = expression.getMethodExpression();
        return method.getReferenceName();
    }

    @Nullable
    public static PsiType getTargetType(
            @NotNull PsiMethodCallExpression expression) {
        final PsiReferenceExpression method = expression.getMethodExpression();
        final PsiExpression qualifierExpression =
                method.getQualifierExpression();
        if (qualifierExpression == null) {
            return null;
        }
        return qualifierExpression.getType();
    }

    public static boolean isEqualsCall(PsiMethodCallExpression expression) {
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        final PsiElement element = methodExpression.resolve();
        if (!(element instanceof PsiMethod)) {
            return false;
        }
        final PsiMethod method = (PsiMethod)element;
        return MethodUtils.isEquals(method);
    }

    public static boolean isMethodCall(
            @NotNull PsiMethodCallExpression expression,
            @NonNls String methodName, int parameterCount, PsiType returnType) {
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        final PsiElement element = methodExpression.resolve();
        if (!(element instanceof PsiMethod)) {
            return false;
        }
        final PsiMethod method = (PsiMethod)element;
        return MethodUtils.methodMatches(method, methodName, parameterCount,
                returnType);
    }
}