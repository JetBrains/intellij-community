/*
 * Copyright 2009 Bas Leijdekkers
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
package com.siyeh.ipp.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

public class ExpressionUtils {

    private ExpressionUtils() {
    }

    @Nullable
    public static Object computeConstantExpression(PsiExpression expression) {
        return computeConstantExpression(expression, false);
    }

    public static Object computeConstantExpression(
            PsiExpression expression, boolean throwExceptionOnOverflow) {
        final Project project = expression.getProject();
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        final PsiConstantEvaluationHelper constantEvaluationHelper =
                psiFacade.getConstantEvaluationHelper();
        return constantEvaluationHelper.computeConstantExpression(expression,
                throwExceptionOnOverflow);
    }

    public static boolean isNegated(PsiExpression expression) {
        final PsiElement parent = expression.getParent();
        if (!(parent instanceof PsiPrefixExpression)) {
            return false;
        }
        final PsiPrefixExpression prefixExpression =
                (PsiPrefixExpression)parent;
        final IElementType tokenType = prefixExpression.getOperationTokenType();
        return JavaTokenType.MINUS.equals(tokenType);
    }
}