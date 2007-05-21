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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ToArrayCallWithZeroLengthArrayArgumentInspection
        extends BaseInspection {

    @Nls @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "to.array.call.with.zero.length.array.argument.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "to.array.call.with.zero.length.array.argument.problem.descriptor",
                infos[0]);
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(PsiElement location) {
        final String replacementText =
                ToArrayCallWithZeroLengthArrayArgumentFix.getReplacementText(
                        location);
        if (replacementText == null) {
            return null;
        }
        return new ToArrayCallWithZeroLengthArrayArgumentFix(replacementText);
    }

    private static class ToArrayCallWithZeroLengthArrayArgumentFix
            extends InspectionGadgetsFix {

        private final String replacementText;

        ToArrayCallWithZeroLengthArrayArgumentFix(
                @NotNull String replacementText) {
            this.replacementText = replacementText;
        }

        @NotNull public String getName() {
            return InspectionGadgetsBundle.message(
                    "to.array.call.with.zero.length.array.argument.quickfix",
                    replacementText);
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement parent = element.getParent();
            if (!(parent instanceof PsiMethodCallExpression)) {
                System.out.println("not a method call");
                return;
            }
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression) parent;
            replaceExpression(methodCallExpression, replacementText);
        }

        @Nullable
        public static String getReplacementText(PsiElement element) {
            if (!(element instanceof PsiMethodCallExpression)) {
                return null;
            }
            final PsiMethodCallExpression expression =
                    (PsiMethodCallExpression) element;
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 1) {
                return null;
            }
            final PsiExpression argument = arguments[0];
            if (!(argument instanceof PsiNewExpression)) {
                return null;
            }
            final PsiNewExpression newExpression = (PsiNewExpression) argument;
            final PsiExpression[] dimensions =
                    newExpression.getArrayDimensions();
            if (dimensions.length != 1) {
                return null;
            }
            final PsiExpression dimension = dimensions[0];
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return null;
            }
            final String text = qualifier.getText();
            final String replacementText = text + ".size()";
            final StringBuilder builder = new StringBuilder();
            appendElementText(expression, dimension, replacementText, builder);
            return builder.toString();
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ToArrayCallWithZeroLengthArrayArgument();
    }

    private static class ToArrayCallWithZeroLengthArrayArgument
            extends BaseInspectionVisitor {

        public void visitMethodCallExpression(
                PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if (!"toArray".equals(methodName)) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            final PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 1) {
                return;
            }
            final PsiExpression argument = arguments[0];
            final PsiType type = argument.getType();
            if (!(type instanceof PsiArrayType)) {
                return;
            }
            if (!ExpressionUtils.isZeroLengthArrayConstruction(
                    argument)) {
                return;
            } else if (argument instanceof PsiReferenceExpression) {
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression) argument;
                final PsiElement element = referenceExpression.resolve();
                if (!(element instanceof PsiField)) {
                    return;
                }
                final PsiField field = (PsiField) element;
                if (!CollectionUtils.isConstantEmptyArray(field)) {
                    return;
                }
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (!ClassUtils.isSubclass(containingClass,
                    "java.util.Collection")) {
                return;
            }
            registerMethodCallError(expression, argument);
//            new java.util.ArrayList().toArray(new Object[0]);
        }
    }
}