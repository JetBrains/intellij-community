/*
 * Copyright 2008-2009 Bas Leijdekkers
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
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class VariableNotUsedInsideIfInspection extends BaseInspection {

    @Override
    @Nls
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "variable.not.used.inside.if.display.name");
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "variable.not.used.inside.if.problem.descriptor");
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new VariableNotUsedInsideIfVisitor();
    }

    private static class VariableNotUsedInsideIfVisitor
            extends BaseInspectionVisitor {

        @Override
        public void visitConditionalExpression(
                PsiConditionalExpression expression) {
            super.visitConditionalExpression(expression);
            final PsiExpression condition = expression.getCondition();
            if (!(condition instanceof PsiBinaryExpression)) {
                return;
            }
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) condition;
            final PsiExpression rhs = binaryExpression.getROperand();
            if (rhs ==  null) {
                return;
            }
            final PsiExpression lhs = binaryExpression.getLOperand();
            final IElementType tokenType =
                    binaryExpression.getOperationTokenType();
            if (tokenType == JavaTokenType.EQEQ) {
                final PsiExpression elseBranch = expression.getElseExpression();
                checkReferences(rhs, lhs, elseBranch);
            } else if (tokenType == JavaTokenType.NE) {
                final PsiExpression thenBranch = expression.getThenExpression();
                checkReferences(rhs, lhs, thenBranch);
            }
        }

        @Override
        public void visitIfStatement(PsiIfStatement statement) {
            super.visitIfStatement(statement);
            final PsiExpression condition = statement.getCondition();
            if (!(condition instanceof PsiBinaryExpression)) {
                return;
            }
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) condition;
            final PsiExpression rhs = binaryExpression.getROperand();
            if (rhs ==  null) {
                return;
            }
            final PsiExpression lhs = binaryExpression.getLOperand();
            final IElementType tokenType =
                    binaryExpression.getOperationTokenType();
            if (tokenType == JavaTokenType.EQEQ) {
                final PsiStatement thenBranch = statement.getThenBranch();
                if (contextExits(thenBranch)) {
                    return;
                }
                final PsiStatement elseBranch = statement.getElseBranch();
                if (contextExits(elseBranch)) {
                    return;
                }
                checkReferences(rhs, lhs, elseBranch);
            } else if (tokenType == JavaTokenType.NE) {
                final PsiStatement thenBranch = statement.getThenBranch();
                if (contextExits(thenBranch)) {
                    return;
                }
                checkReferences(rhs, lhs, thenBranch);
            }
        }

        private void checkReferences(PsiExpression left, PsiExpression right,
                                     PsiElement context) {
            if (context == null) {
                return;
            }
            if ("null".equals(left.getText())) {
                checkReferences(right, context);
            } else if ("null".equals(right.getText())) {
                checkReferences(left, context);
            }
        }

        private void checkReferences(PsiExpression expression,
                                     PsiElement context) {
            if (!(expression instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) expression;
            final PsiElement target = referenceExpression.resolve();
            if (!(target instanceof PsiVariable)) {
                return;
            }
            final PsiVariable variable = (PsiVariable) target;
            if (VariableAccessUtils.variableIsUsed(variable, context)) {
                return;
            }
            registerError(referenceExpression);
        }

        private static boolean contextExits(PsiElement context) {
            if (context instanceof PsiBlockStatement) {
                final PsiBlockStatement blockStatement =
                        (PsiBlockStatement) context;
                final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
                final PsiStatement[] statements = codeBlock.getStatements();
                if (statements.length == 0) {
                    return true;
                }
                final PsiStatement lastStatement =
                        statements[statements.length - 1];
                return statementExits(lastStatement);
            } else {
                return statementExits(context);
            }
        }

        private static boolean statementExits(PsiElement context) {
            return context instanceof PsiReturnStatement ||
                   context instanceof PsiThrowStatement ||
                   context instanceof PsiBreakStatement ||
                   context instanceof PsiContinueStatement;
        }
    }
}
