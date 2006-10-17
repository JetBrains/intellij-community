/*
 * Copyright 2006 Bas Leijdekkers
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
package com.siyeh.ig.controlflow;

import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.InspectionGadgetsBundle;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DoubleNegationInspection extends StatementInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("double.negation.display.name");
    }

    @Nls
    @NotNull
    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "double.negation.problem.descriptor");
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new DoubleNegationFix();
    }

    private static class DoubleNegationFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message("double.negation.quickfix");
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiPrefixExpression expression =
                    (PsiPrefixExpression) descriptor.getPsiElement();
            PsiExpression operand = expression.getOperand();
            while (operand instanceof PsiParenthesizedExpression) {
                final PsiParenthesizedExpression parenthesizedExpression =
                        (PsiParenthesizedExpression) operand;
                operand = parenthesizedExpression.getExpression();
            }
            if (operand instanceof PsiPrefixExpression) {
                final PsiPrefixExpression prefixExpression =
                        (PsiPrefixExpression) operand;
                final PsiExpression innerOperand = prefixExpression.getOperand();
                if (innerOperand == null) {
                    return;
                }
                expression.replace(innerOperand);
            } else if (operand instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression) operand;
                final PsiExpression lhs = binaryExpression.getLOperand();
                final String lhsText = lhs.getText();
                final StringBuilder builder =
                        new StringBuilder(lhsText);
                builder.append("==");
                final PsiExpression rhs = binaryExpression.getROperand();
                if (rhs != null) {
                    final String rhsText = rhs.getText();
                    builder.append(rhsText);
                }
                final PsiManager manager = binaryExpression.getManager();
                final PsiElementFactory factory = manager.getElementFactory();
                final PsiExpression newExpression =
                        factory.createExpressionFromText(builder.toString(),
                                binaryExpression);
                expression.replace(newExpression);
            }
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new DoubleNegationVisitor();
    }

    private static class DoubleNegationVisitor extends BaseInspectionVisitor {

        public void visitPrefixExpression(PsiPrefixExpression expression) {
            super.visitPrefixExpression(expression);
            final IElementType tokenType = expression.getOperationTokenType();
            if (!JavaTokenType.EXCL.equals(tokenType)) {
                return;
            }
            checkParent(expression);
        }

        public void visitBinaryExpression(PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            final IElementType tokenType = expression.getOperationTokenType();
            if (!JavaTokenType.NE.equals(tokenType)) {
                return;
            }
            checkParent(expression);
        }

        private void checkParent(PsiExpression expression) {
            PsiElement parent = expression.getParent();
            while (parent instanceof PsiParenthesizedExpression) {
                parent = parent.getParent();
            }
            if (!(parent instanceof PsiPrefixExpression)) {
                return;
            }
            final PsiPrefixExpression prefixExpression =
                    (PsiPrefixExpression) parent;
            final IElementType parentTokenType =
                    prefixExpression.getOperationTokenType();
            if (!JavaTokenType.EXCL.equals(parentTokenType)) {
                return;
            }
            registerError(prefixExpression);
        }
    }
}