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
package com.siyeh.ig.junit;

import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AssertEqualsBetweenInconvertibleTypesInspection
        extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "assertequals.between.inconvertible.types.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final PsiType comparedType = (PsiType)infos[0];
        final PsiType comparisonType = (PsiType)infos[1];
        return InspectionGadgetsBundle.message(
                "assertequals.between.inconvertible.types.problem.descriptor",
                comparedType.getPresentableText(),
                comparisonType.getPresentableText());
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new AssertEqualsBetweenInconvertibleTypesVisitor();
    }

    private static class AssertEqualsBetweenInconvertibleTypesVisitor
            extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 2 && arguments.length != 3) {
                return;
            }
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            @NonNls final String methodName =
                    methodExpression.getReferenceName();
            if (!"assertEquals".equals(methodName)) {
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (!ClassUtils.isSubclass(containingClass, "junit.framework.Assert") &&
                    !ClassUtils.isSubclass(containingClass, "org.junit.Assert")) {
                return;
            }
            final PsiExpression expression1 = arguments[arguments.length - 1];
            final PsiExpression expression2 = arguments[arguments.length - 2];
            final PsiType type1 = expression1.getType();
            if (type1 == null) {
                return;
            }
            final PsiType type2 = expression2.getType();
            if (type2 == null) {
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            final PsiType parameterType1 =
                    parameters[parameters.length - 1].getType();
            final PsiType parameterType2 = 
                    parameters[parameters.length - 2].getType();
            if (!parameterType1.equals(parameterType2)) {
                return;
            }
            if (TypeConversionUtil.areTypesConvertible(type1, type2)) {
                return;
            }
            registerMethodCallError(expression, type1, type2);
        }
    }
}