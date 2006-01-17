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
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

public class LoopConditionNotUpdatedInsideLoopInspection
        extends StatementInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "loop.condition.not.updated.inside.loop.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    @Nullable
    protected String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message(
                "loop.condition.not.updated.inside.loop.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new LoopConditionNotUpdatedInsideLoopVisitor();
    }

    private static class LoopConditionNotUpdatedInsideLoopVisitor
            extends BaseInspectionVisitor {

        public void visitWhileStatement(PsiWhileStatement statement) {
            super.visitWhileStatement(statement);
            final PsiExpression condition = statement.getCondition();
            checkCondition(condition, statement);
        }

        public void visitDoWhileStatement(PsiDoWhileStatement statement) {
            super.visitDoWhileStatement(statement);
            final PsiExpression condition = statement.getCondition();
            checkCondition(condition, statement);
        }

        public void visitForStatement(PsiForStatement statement) {
            super.visitForStatement(statement);
            final PsiExpression condition = statement.getCondition();
            final boolean notUpdated = checkCondition(condition, statement);
            if (notUpdated) {
                registerError(condition);
            }
        }

        private boolean checkCondition(@Nullable PsiExpression condition,
                                    @NotNull PsiStatement context) {
            if (condition == null) {
                return false;
            }
            if (condition instanceof PsiInstanceOfExpression) {
                final PsiInstanceOfExpression instanceOfExpression =
                        (PsiInstanceOfExpression)condition;
                final PsiExpression operand = instanceOfExpression.getOperand();
                if (checkCondition(operand, context)) {
                    registerError(operand);
                }
                return false;
            } else if (condition instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression)condition;
                PsiExpression lhs = binaryExpression.getLOperand();
                PsiExpression rhs = binaryExpression.getROperand();
                if (rhs == null) {
                    return false;
                }
                lhs = ParenthesesUtils.stripParentheses(lhs);
                rhs = ParenthesesUtils.stripParentheses(rhs);
                if (ComparisonUtils.isComparison(binaryExpression)) {
                    if (PsiUtil.isConstantExpression(lhs)) {
                        if (checkCondition(rhs, context)) {
                            registerError(rhs);
                        }
                        return false;
                    } else if (PsiUtil.isConstantExpression(rhs)) {
                        if (checkCondition(lhs, context)) {
                            registerError(lhs);
                        }
                        return false;
                    } else if (lhs instanceof PsiReferenceExpression &&
                            rhs instanceof PsiReferenceExpression){
                        if (checkCondition(lhs, context) &&
                                checkCondition(rhs, context)) {
                            registerError(lhs);
                            registerError(rhs);
                        }
                        return false;
                    }
                }
            } else if (condition instanceof PsiReferenceExpression) {
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression)condition;
                final PsiElement element = referenceExpression.resolve();
                if (element instanceof PsiLocalVariable) {
                    final PsiLocalVariable variable = (PsiLocalVariable)element;
                    if (!VariableAccessUtils.variableIsAssigned(variable,
                            context)) {
                        return true;
                    }
                } else if (element instanceof PsiParameter) {
                    final PsiParameter parameter = (PsiParameter)element;
                    if (!VariableAccessUtils.variableIsAssigned(parameter,
                            context)) {
                        return true;
                    }
                }
            } else if (condition instanceof PsiPrefixExpression) {
                final PsiPrefixExpression prefixExpression =
                        (PsiPrefixExpression)condition;
                final PsiJavaToken sign = prefixExpression.getOperationSign();
                final IElementType tokenType = sign.getTokenType();
                if (JavaTokenType.EXCL.equals(tokenType) ||
                        JavaTokenType.MINUS.equals(tokenType) ||
                        JavaTokenType.PLUS.equals(tokenType)) {
                    final PsiExpression operand = prefixExpression.getOperand();
                    if (checkCondition(operand, context)) {
                        registerError(operand);
                    }
                    return false;
                }
            }
            return false;
        }
    }
}