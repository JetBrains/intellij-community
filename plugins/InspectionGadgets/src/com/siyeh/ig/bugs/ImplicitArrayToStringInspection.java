/*
 * Copyright 2007-2010 Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImplicitArrayToStringInspection extends BaseInspection {

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "implicit.array.to.string.display.name");
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
        if (infos[0] instanceof PsiMethodCallExpression) {
            return InspectionGadgetsBundle.message(
                    "implicit.array.to.string.method.call.problem.descriptor");
        } else {
            return InspectionGadgetsBundle.message(
                    "implicit.array.to.string.problem.descriptor");
        }
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        final PsiExpression expression = (PsiExpression)infos[0];
        final PsiArrayType type = (PsiArrayType) expression.getType();
        if (type != null) {
            final PsiType componentType = type.getComponentType();
            if (componentType instanceof PsiArrayType) {
                return new ImplicitArrayToStringFix(true);
            }
        }
        return new ImplicitArrayToStringFix(false);
    }

    private static class ImplicitArrayToStringFix extends InspectionGadgetsFix {

        private final boolean deepString;

        ImplicitArrayToStringFix(boolean deepString) {
            this.deepString = deepString;
        }

        @NotNull
        public String getName() {
            @NonNls final String expressionText;
            if (deepString) {
                expressionText = "java.util.Arrays.deepToString()";
            } else {
                expressionText = "java.util.Arrays.toString()";
            }
            return InspectionGadgetsBundle.message(
                    "implicit.array.to.string.quickfix", expressionText);
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiExpression expression =
                    (PsiExpression)descriptor.getPsiElement();
            final String expressionText = expression.getText();
            @NonNls final String newExpressionText;
            if (deepString) {
                newExpressionText =
                        "java.util.Arrays.deepToString(" + expressionText + ')';
            } else {
                newExpressionText =
                        "java.util.Arrays.toString(" + expressionText + ')';
            }
            replaceExpressionAndShorten(expression, newExpressionText);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ImplicitArrayToStringVisitor();
    }

    private static class ImplicitArrayToStringVisitor
            extends BaseInspectionVisitor {

        @Override public void visitReferenceExpression(
                PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            if (!isImplicitArrayToStringCall(expression)) {
                return;
            }
            registerError(expression, expression);
        }

        @Override public void visitNewExpression(PsiNewExpression expression) {
            super.visitNewExpression(expression);
            if (!isImplicitArrayToStringCall(expression)) {
                return;
            }
            registerError(expression, expression);
        }

        @Override public void visitMethodCallExpression(
                PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (!isImplicitArrayToStringCall(expression)) {
                return;
            }
            registerError(expression, expression);
        }

        private static boolean isImplicitArrayToStringCall(
                PsiExpression expression) {
            final PsiType type = expression.getType();
            if (!(type instanceof PsiArrayType)) {
                return false;
            }
            final PsiElement parent = expression.getParent();
            if (parent instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression) parent;
                final IElementType tokenType =
                        binaryExpression.getOperationTokenType();
                if (!JavaTokenType.PLUS.equals(tokenType)) {
                    return false;
                }
                final PsiExpression lhs = binaryExpression.getLOperand();
                if (lhs != expression) {
                    final PsiType lhsType = lhs.getType();
                    return !(lhsType == null ||
                            !lhsType.equalsToText("java.lang.String"));
                }
                final PsiExpression rhs = binaryExpression.getROperand();
                if (rhs != null && rhs != expression) {
                    final PsiType rhsType = rhs.getType();
                    return !(rhsType == null ||
                            !rhsType.equalsToText("java.lang.String"));
                }
            } else if (parent instanceof PsiExpressionList) {
                final PsiExpressionList expressionList =
                        (PsiExpressionList) parent;
                final PsiElement grandParent = expressionList.getParent();
                if (!(grandParent instanceof PsiMethodCallExpression)) {
                    return false;
                }
                final PsiMethodCallExpression methodCallExpression =
                        (PsiMethodCallExpression) grandParent;
                final PsiReferenceExpression methodExpression =
                        methodCallExpression.getMethodExpression();
                @NonNls final String methodName =
                        methodExpression.getReferenceName();
                if (!"print".equals(methodName) &&
                        !"println".equals(methodName) &&
                        !"printf".equals(methodName) &&
                        !"format".equals(methodName)) {
                    return false;
                }
                final PsiExpression qualifier =
                        methodExpression.getQualifierExpression();
                if (TypeUtils.expressionHasTypeOrSubtype(qualifier,
                        "java.io.PrintStream", "java.util.Formatter",
                        "java.io.PrintWriter") == null) {
                    if (!(qualifier instanceof PsiReferenceExpression)) {
                        return false;
                    }
                    final PsiReferenceExpression referenceExpression =
                            (PsiReferenceExpression)qualifier;
                    final PsiElement target = referenceExpression.resolve();
                    if (!(target instanceof PsiClass)) {
                        return false;
                    }
                    final PsiClass aClass = (PsiClass)target;
                    final String className = aClass.getQualifiedName();
                    if (!"java.lang.String".equals(className)) {
                        return false;
                    }
                }
                final PsiArrayType arrayType = (PsiArrayType)type;
                final PsiType componentType = arrayType.getComponentType();
                return componentType != PsiType.CHAR;
            }
            return false;
        }
    }
}