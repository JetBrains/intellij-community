/*
 * Copyright 2005 Bas Leijdekkers
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SuspiciousToArrayCallInspection extends StatementInspection {

    private String errorString = InspectionGadgetsBundle.message("suspicious.to.array.call.problem.descriptor");

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("suspicious.to.array.call.display.name");
    }

    @Nullable
    protected String buildErrorString(PsiElement location) {
        return errorString;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SuspiciousToArrayCallVisitor();
    }

    private class SuspiciousToArrayCallVisitor
            extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            @NonNls final String methodName = methodExpression.getReferenceName();
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
            if (aClass == null || !ClassUtils.isSubclass(aClass, "java.util.Collection")) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 1) {
                return;
            }
            final PsiExpression argument = arguments[0];
            final PsiType argumentType = argument.getType();
            if (!(argumentType instanceof PsiArrayType)) {
                return;
            }
            final PsiArrayType arrayType = (PsiArrayType)argumentType;
            if (collectionAndCallTypesMatch(classType, arrayType, expression)) {
                return;
            }
            registerError(argument);
        }

        private boolean collectionAndCallTypesMatch(
                @NotNull PsiClassType collectionType,
                @NotNull PsiArrayType callType,
                @NotNull PsiMethodCallExpression expression) {
            if (collectionType.hasParameters()) {
                final PsiType[] parameters = collectionType.getParameters();
                if (parameters.length != 1) {
                    return true;
                }
                final PsiType parameter = parameters[0];
                final PsiType componentType = callType.getComponentType();
                if (!parameter.equals(componentType)) {
                    errorString =
                      InspectionGadgetsBundle.message("suspicious.to.array.call.problem.descriptor1", parameter.getPresentableText());
                    return false;
                }
            } else {
                final PsiElement parent = expression.getParent();
                if (!(parent instanceof PsiTypeCastExpression)) {
                    return true;
                }
                final PsiTypeCastExpression castExpression =
                        (PsiTypeCastExpression)parent;
                final PsiTypeElement castTypeElement =
                        castExpression.getCastType();
                if (castTypeElement == null) {
                    return true;
                }
                final PsiType castType = castTypeElement.getType();
                if (!castType.equals(callType)) {
                    errorString =
                      InspectionGadgetsBundle.message("suspicious.to.array.call.problem.descriptor2", castType.getPresentableText());
                    return false;
                }
            }
            return true;
        }
    }
}