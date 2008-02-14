/*
 * Copyright 2008 Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnnecessaryCallToStringValueOfInspection extends BaseInspection {

    @Nls
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "unnecessary.call.to.string.valueof.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        final PsiExpression expression = (PsiExpression) infos[0];
        return InspectionGadgetsBundle.message(
                "unnecessary.call.to.string.valueof.problem.descriptor",
                expression);
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        final PsiExpression expression = (PsiExpression) infos[0];
        return new UnnecessaryCallToStringValueOfFix(expression.getText());
    }

    private static class UnnecessaryCallToStringValueOfFix
            extends InspectionGadgetsFix {

        private final String replacementText;

        UnnecessaryCallToStringValueOfFix(String replacementText) {
            this.replacementText = replacementText;
        }

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "unnecessary.call.to.string.valueof.quickfix",
                    replacementText);
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) descriptor.getPsiElement();
            final PsiExpressionList argumentList =
                    methodCallExpression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 1) {
                return;
            }
            final PsiExpression argument = arguments[0];
            methodCallExpression.replace(argument);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryCallToStringValueOfVisitor();
    }

    private static class UnnecessaryCallToStringValueOfVisitor
            extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final String referenceName = methodExpression.getReferenceName();
            if (!"valueOf".equals(referenceName)) {
                return;
            }
            final PsiElement parent = expression.getParent();
            if (!(parent instanceof PsiBinaryExpression)) {
                return;
            }
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) parent;
            final PsiType type = binaryExpression.getType();
            if (!TypeUtils.typeEquals("java.lang.String", type)) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 1) {
                return;
            }
            final PsiExpression argument = arguments[0];
            final PsiType argumentType = argument.getType();
            if (argumentType instanceof PsiArrayType) {
                final PsiArrayType arrayType = (PsiArrayType) argumentType;
                final PsiType componentType = arrayType.getComponentType();
                if (PsiType.CHAR == componentType) {
                    return;
                }
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            final String qualifiedName = aClass.getQualifiedName();
            if (!"java.lang.String".equals(qualifiedName)) {
                return;
            }
            registerError(expression, argument);
        }
    }
}
