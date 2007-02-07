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
package com.siyeh.ig.bugs;

import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.InspectionGadgetsBundle;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImplicitArrayToStringInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "implicit.array.to.string.display.name");
    }

    @Nls @NotNull
    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "implicit.array.to.string.problem.descriptor");
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(PsiElement location) {
        final PsiReferenceExpression expression =
                (PsiReferenceExpression) location;
        final PsiArrayType type = (PsiArrayType) expression.getType();
        if (type != null) {
            final PsiType componentType = type.getComponentType();
            if (componentType instanceof PsiArrayType) {
                return new ImplicitArrayToStringFix("Arrays.deepToString()");
            }
        }
        return new ImplicitArrayToStringFix("Arrays.toString()");
    }

    private static class ImplicitArrayToStringFix extends InspectionGadgetsFix {

        private final String expression;

        ImplicitArrayToStringFix(String expression) {
            this.expression = expression;
        }

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "implicit.array.to.string.quickfix", expression);
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiReferenceExpression expression =
                    (PsiReferenceExpression) descriptor.getPsiElement();
            final PsiArrayType type = (PsiArrayType) expression.getType();
            if (type == null) {
                return;
            }
            final PsiType componentType = type.getComponentType();
            final String expressionText = expression.getText();
            final String newExpressionText;
            if (componentType instanceof PsiArrayType) {
                newExpressionText =
                        "java.util.Arrays.deepToString(" + expressionText + ')';
            } else {
                newExpressionText =
                        "java.util.Arrays.toString(" + expressionText + ')';
            }
            replaceExpression(expression, newExpressionText);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ImplicitArrayToStringVisitor();
    }

    private static class ImplicitArrayToStringVisitor
            extends BaseInspectionVisitor {

        public void visitReferenceExpression(
                PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            final PsiType type = expression.getType();
            if (!(type instanceof PsiArrayType)) {
                return;
            }
            final PsiElement parent = expression.getParent();
            if (parent instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression) parent;
                final IElementType tokenType =
                        binaryExpression.getOperationTokenType();
                if (!JavaTokenType.PLUS.equals(tokenType)) {
                    return;
                }
                final PsiExpression lhs = binaryExpression.getLOperand();
                if (lhs != expression) {
                    final PsiType lhsType = lhs.getType();
                    if (lhsType == null ||
                            !lhsType.equalsToText("java.lang.String")) {
                        return;
                    }
                    registerError(expression);
                }
                final PsiExpression rhs = binaryExpression.getROperand();
                if (rhs != null && rhs != expression) {
                    final PsiType rhsType = rhs.getType();
                    if (rhsType == null ||
                            !rhsType.equalsToText("java.lang.String")) {
                        return;
                    }
                    registerError(expression);
                }
            } else if (parent instanceof PsiExpressionList) {
                final PsiExpressionList expressionList =
                        (PsiExpressionList) parent;
                final PsiElement grandParent = expressionList.getParent();
                if (!(grandParent instanceof PsiMethodCallExpression)) {
                    return;
                }
                final PsiMethodCallExpression methodCallExpression =
                        (PsiMethodCallExpression) grandParent;
                final PsiReferenceExpression methodExpression =
                        methodCallExpression.getMethodExpression();
                final String methodName = methodExpression.getReferenceName();
                if (!"print".equals(methodName) ||
                        !"println".equals(methodName)) {
                    return;
                }
                final PsiMethod method = methodCallExpression.resolveMethod();
                if (method == null) {
                    return;
                }
                final PsiClass containingClass = method.getContainingClass();
                final String qualifiedName = containingClass.getQualifiedName();
                if (!"java.io.PrintStream".equals(qualifiedName)) {
                    return;
                }
                registerError(expression);
            }
        }
    }
}