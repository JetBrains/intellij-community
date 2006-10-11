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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.ig.psiutils.BoolUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LoopConditionNotUpdatedInsideLoopInspection
        extends StatementInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "loop.condition.not.updated.inside.loop.display.name");
    }

    @NotNull
    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
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
            check(condition, statement);
        }

        public void visitDoWhileStatement(PsiDoWhileStatement statement) {
            super.visitDoWhileStatement(statement);
            final PsiExpression condition = statement.getCondition();
            check(condition, statement);
        }

        public void visitForStatement(PsiForStatement statement) {
            super.visitForStatement(statement);
            final PsiExpression condition = statement.getCondition();
            check(condition, statement);
        }

        private void check(PsiExpression condition, PsiStatement statement) {
            final List<PsiExpression> notUpdated =
                    new SmartList<PsiExpression>();
            if (checkCondition(condition, statement, notUpdated)) {
                if (notUpdated.isEmpty()) {
                    // condition involves only final variables and/or constants,
                    // flag the whole condition
                    // Maybe this should show a different error message, like
                    // "loop condition cannot change",
                    // "loop condition is never updated" or so
                    if (!BoolUtils.isTrue(condition)) {
                        registerError(condition);
                    }
                } else {
                    for (PsiExpression expression : notUpdated) {
                        registerError(expression);
                    }
                }
            }
        }

        private static boolean checkCondition(@Nullable PsiExpression condition,
                                              @NotNull PsiStatement context,
                                              List<PsiExpression> notUpdated) {
            if (condition == null) {
                return false;
            }
            if (PsiUtil.isConstantExpression(condition) ||
                    PsiKeyword.NULL.equals(condition.getText())) {
                return true;
            }
            if (condition instanceof PsiInstanceOfExpression) {
                final PsiInstanceOfExpression instanceOfExpression =
                        (PsiInstanceOfExpression)condition;
                final PsiExpression operand = instanceOfExpression.getOperand();
                return checkCondition(operand, context, notUpdated);
            } else if (condition instanceof PsiParenthesizedExpression) {
                // catch stuff like "while ((x)) { ... }"
                final PsiExpression expression =
                        ((PsiParenthesizedExpression)condition).getExpression();
                return checkCondition(expression, context, notUpdated);
            } else if (condition instanceof PsiBinaryExpression) {
                // while (value != x) { ... }
                // while (value != (x + y)) { ... }
                // while (b1 && b2) { ... }
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression)condition;
                final PsiExpression lhs = binaryExpression.getLOperand();
                final PsiExpression rhs = binaryExpression.getROperand();
                if (rhs == null) {
                    return false;
                }
                if (checkCondition(lhs, context, notUpdated)) {
                    return checkCondition(rhs, context, notUpdated);
                }
            } else if (condition instanceof PsiReferenceExpression) {
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression)condition;
                final PsiElement element = referenceExpression.resolve();

                if (element instanceof PsiField) {
                    final PsiField field = (PsiField)element;
                    final PsiType type = field.getType();
                    if (field.hasModifierProperty(PsiModifier.FINAL) &&
                            type.getArrayDimensions() == 0) {
                        if (field.hasModifierProperty(PsiModifier.STATIC)) {
                            return true;
                        }
                        final PsiExpression qualifier =
                            referenceExpression.getQualifierExpression();
                        if (qualifier == null) {
                            return true;
                        } else if (checkCondition(qualifier, context,
                                notUpdated)) {
                            return true;
                        }
                    }
                } else if (element instanceof PsiVariable) {
                    final PsiVariable variable = (PsiVariable)element;
                    if (variable.hasModifierProperty(PsiModifier.FINAL)) {
                        // final variables cannot be updated, don't bother to
                        // flag them
                        return true;
                    } else if (element instanceof PsiLocalVariable) {
                        if (!VariableAccessUtils.variableIsAssigned(variable,
                                context)) {
                            notUpdated.add(referenceExpression);
                            return true;
                        }
                    } else if (element instanceof PsiParameter) {
                        if (!VariableAccessUtils.variableIsAssigned(variable,
                                context)) {
                            notUpdated.add(referenceExpression);
                            return true;
                        }
                    }
                }
            } else if (condition instanceof PsiPrefixExpression) {
                final PsiPrefixExpression prefixExpression =
                        (PsiPrefixExpression)condition;
                final PsiJavaToken sign = prefixExpression.getOperationSign();
                final IElementType tokenType = sign.getTokenType();
                if (JavaTokenType.EXCL.equals(tokenType) ||
                        JavaTokenType.PLUS.equals(tokenType) ||
                        JavaTokenType.MINUS.equals(tokenType)) {
                    final PsiExpression operand = prefixExpression.getOperand();
                    return checkCondition(operand, context, notUpdated);
                }
            } else if (condition instanceof PsiArrayAccessExpression) {
                // Actually the contents of the array could change nevertheless
                // if it is accessed through a different reference like this:
                //   int[] local_ints = new int[]{1, 2};
                //   int[] other_ints = local_ints;
                //   while (local_ints[0] > 0) { other_ints[0]--; }
                //
                // Keep this check?
                final PsiArrayAccessExpression accessExpression =
                        (PsiArrayAccessExpression)condition;
                final PsiExpression indexExpression =
                        accessExpression.getIndexExpression();
                return checkCondition(indexExpression, context, notUpdated)
                        && checkCondition(accessExpression.getArrayExpression(),
                        context, notUpdated);
            } else if (condition instanceof PsiConditionalExpression) {
                final PsiConditionalExpression conditionalExpression =
                        (PsiConditionalExpression)condition;

                final PsiExpression thenExpression =
                        conditionalExpression.getThenExpression();
                final PsiExpression elseExpression =
                        conditionalExpression.getElseExpression();
                if (thenExpression == null || elseExpression == null) {
                    return false;
                }
                return checkCondition(conditionalExpression.getCondition(),
                        context, notUpdated)
                        && checkCondition(thenExpression, context, notUpdated)
                        && checkCondition(elseExpression, context, notUpdated);
            } else if (condition instanceof PsiThisExpression) {
                return true;
            }
            return false;
        }
    }
}