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

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MethodCallUtils {
    private MethodCallUtils() {
        super();

    }

    public static @Nullable String getMethodName(@NotNull PsiMethodCallExpression expression) {
        final PsiReferenceExpression method = expression.getMethodExpression();
        if (method == null) {
            return null;
        }
        return method.getReferenceName();
    }

    public static @Nullable PsiType getTargetType(@NotNull PsiMethodCallExpression expression) {
        final PsiReferenceExpression method = expression.getMethodExpression();
        if (method == null) {
            return null;
        }
        final PsiExpression qualifierExpression = method.getQualifierExpression();
        if (qualifierExpression == null) {
            return null;
        }
        return qualifierExpression.getType();
    }
}
