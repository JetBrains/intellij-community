/*
 * Copyright 2005-2007 Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SuspiciousToArrayCallInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "suspicious.to.array.call.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        final PsiType type = (PsiType)infos[0];
        return InspectionGadgetsBundle.message(
                "suspicious.to.array.call.problem.descriptor",
                type.getPresentableText());
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SuspiciousToArrayCallVisitor();
    }

    private static class SuspiciousToArrayCallVisitor
            extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            @NonNls final String methodName =
                    methodExpression.getReferenceName();
            if (!"toArray".equals(methodName)) {
                return;
            }
            final PsiExpression qualifierExpression =
                    methodExpression.getQualifierExpression();
            if (qualifierExpression == null) {
                return;
            }
            final PsiType type = qualifierExpression.getType();
            if (!(type instanceof PsiClassType)) {
                return;
            }
            final PsiClassType classType = (PsiClassType)type;
            final PsiClass aClass = classType.resolve();
            if (aClass == null ||
                    !ClassUtils.isSubclass(aClass, "java.util.Collection")) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 1) {
                return;
            }
            final PsiExpression argument = arguments[0];
            checkCollectionAndArrayTypes(classType, argument, expression);
        }

        private void checkCollectionAndArrayTypes(
                @NotNull PsiClassType collectionType,
                @NotNull PsiExpression argument,
                @NotNull PsiMethodCallExpression expression) {
            final PsiType argumentType = argument.getType();
            if (!(argumentType instanceof PsiArrayType)) {
                return;
            }
            final PsiArrayType arrayType = (PsiArrayType)argumentType;
            if (collectionType.hasParameters()) {
                final PsiType[] parameters = collectionType.getParameters();
                if (parameters.length != 1) {
                    return;
                }
                final PsiType parameter = parameters[0];
                final PsiType type;
                final PsiType componentType = arrayType.getComponentType();
                if (parameter instanceof PsiWildcardType) {
                    final PsiWildcardType wildcardType = (PsiWildcardType)parameter;
                    type = wildcardType.getBound();
                } else if (parameter instanceof PsiClassType) {
                    final PsiClassType classType = (PsiClassType)parameter;
                    final String classTypeText = classType.getCanonicalText();
                    final String componentTypeText =
                            componentType.getCanonicalText();
                    // compare text because in build #6795
                    // PsiType: List<?> is not equal to PsiType: List<?>
                    type = classType.rawType();
                    if (!type.equals(componentType) &&
                            !classTypeText.equals(componentTypeText)) {
                        registerError(argument, classType);
                    }
                    return;
                } else {
                    type = parameter;
                }
                if (!type.equals(componentType)) {
                    registerError(argument, type);
                }
            } else {
                final PsiElement parent = expression.getParent();
                if (!(parent instanceof PsiTypeCastExpression)) {
                    return;
                }
                final PsiTypeCastExpression castExpression =
                        (PsiTypeCastExpression)parent;
                final PsiTypeElement castTypeElement =
                        castExpression.getCastType();
                if (castTypeElement == null) {
                    return;
                }
                final PsiType castType = castTypeElement.getType();
                if (!castType.equals(arrayType)) {
                    registerError(argument, arrayType.getComponentType());
                }
            }
        }
    }
}