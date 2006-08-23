/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

public class StringConcatenationInLoopsInspection extends ExpressionInspection {

    /** @noinspection PublicField */
    public boolean m_ignoreUnlessAssigned = false;

    public String getID() {
        return "StringContatenationInLoop";
    }

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "string.concatenation.in.loops.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "string.concatenation.in.loops.problem.descriptor");
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(
                InspectionGadgetsBundle.message(
                        "string.concatenation.in.loops.only.option"),
                this, "m_ignoreUnlessAssigned");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StringConcatenationInLoopsVisitor();
    }

    private class StringConcatenationInLoopsVisitor
            extends BaseInspectionVisitor {

        public void visitBinaryExpression(
                @NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if (expression.getROperand() == null) {
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUS)) {
                return;
            }
            final PsiType type = expression.getType();
            if (type == null) {
                return;
            }
            if (!TypeUtils.isJavaLangString(type)) {
                return;
            }
            if (!ControlFlowUtils.isInLoop(expression)) {
                return;
            }
            if (ControlFlowUtils.isInExitStatement(expression)) {
                return;
            }
            if (isEvaluatedAtCompileTime(expression)) {
                return;
            }
            if (containingStatementExits(expression)) {
                return;
            }
            if (m_ignoreUnlessAssigned && !isOnRHSOfAssignment(expression)) {
                return;
            }
            registerError(sign);
        }

        public void visitAssignmentExpression(
                @NotNull PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);
            if (expression.getRExpression() == null) {
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSEQ)) {
                return;
            }
            final PsiExpression lhs = expression.getLExpression();
            final PsiType type = lhs.getType();
            if (type == null) {
                return;
            }
            if (!TypeUtils.isJavaLangString(type)) {
                return;
            }
            if (!ControlFlowUtils.isInLoop(expression)) {
                return;
            }
            if (ControlFlowUtils.isInExitStatement(expression)) {
                return;
            }
            if (containingStatementExits(expression)) {
                return;
            }
            registerError(sign);
        }

        private boolean containingStatementExits(PsiElement element) {
            final PsiStatement newExpressionStatement =
                    PsiTreeUtil.getParentOfType(element, PsiStatement.class);
            if (newExpressionStatement == null) {
                return containingStatementExits(element);
            }
            final PsiStatement parentStatement =
                    PsiTreeUtil.getParentOfType(newExpressionStatement,
                            PsiStatement.class);
            return !ControlFlowUtils.statementMayCompleteNormally(
                    parentStatement);
        }

        private boolean isEvaluatedAtCompileTime(PsiExpression expression) {
            if (expression instanceof PsiLiteralExpression) {
                return true;
            }
            if (expression instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression)expression;
                final PsiExpression lhs = binaryExpression.getLOperand();
                final PsiExpression rhs = binaryExpression.getROperand();
                return isEvaluatedAtCompileTime(lhs) &&
                        isEvaluatedAtCompileTime(rhs);
            }
            if (expression instanceof PsiPrefixExpression) {
                final PsiPrefixExpression prefixExpression =
                        (PsiPrefixExpression)expression;
                final PsiExpression operand = prefixExpression.getOperand();
                return isEvaluatedAtCompileTime(operand);
            }
            if (expression instanceof PsiReferenceExpression) {
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression)expression;
                final PsiElement qualifier = referenceExpression.getQualifier();
                if (qualifier instanceof PsiThisExpression) {
                    return false;
                }
                final PsiElement element = referenceExpression.resolve();
                if (element instanceof PsiField) {
                    final PsiField field = (PsiField)element;
                    final PsiExpression initializer = field.getInitializer();
                    return field.hasModifierProperty(PsiModifier.FINAL) &&
                            isEvaluatedAtCompileTime(initializer);
                }
                if (element instanceof PsiVariable) {
                    final PsiVariable variable = (PsiVariable)element;
                    if (PsiTreeUtil.isAncestor(variable, expression, true)) {
                        return false;
                    }
                    final PsiExpression initializer = variable.getInitializer();
                    return variable.hasModifierProperty(PsiModifier.FINAL) &&
                            isEvaluatedAtCompileTime(initializer);
                }
            }
            if (expression instanceof PsiParenthesizedExpression) {
                final PsiParenthesizedExpression parenthesizedExpression =
                        (PsiParenthesizedExpression)expression;
                final PsiExpression unparenthesizedExpression =
                        parenthesizedExpression.getExpression();
                return isEvaluatedAtCompileTime(unparenthesizedExpression);
            }
            if (expression instanceof PsiConditionalExpression) {
                final PsiConditionalExpression conditionalExpression =
                        (PsiConditionalExpression)expression;
                final PsiExpression condition = conditionalExpression.getCondition();
                final PsiExpression thenExpression =
                        conditionalExpression.getThenExpression();
                final PsiExpression elseExpression =
                        conditionalExpression.getElseExpression();
                return isEvaluatedAtCompileTime(condition) &&
                        isEvaluatedAtCompileTime(thenExpression) &&
                        isEvaluatedAtCompileTime(elseExpression);
            }
            if (expression instanceof PsiTypeCastExpression) {
                final PsiTypeCastExpression typeCastExpression =
                        (PsiTypeCastExpression)expression;
                final PsiTypeElement castType = typeCastExpression.getCastType();
                if (castType == null) {
                    return false;
                }
                final PsiType type = castType.getType();
                return TypeUtils.typeEquals("java.lang.String", type);
            }
            return false;
        }

        private boolean isOnRHSOfAssignment(PsiExpression expression) {
            final PsiElement parent = expression.getParent();
            if (parent instanceof PsiParenthesizedExpression) {
                return isOnRHSOfAssignment((PsiExpression)parent);
            }
            if (parent instanceof PsiAssignmentExpression) {
                return true;
            }
            return parent instanceof PsiBinaryExpression &&
                    isOnRHSOfAssignment((PsiExpression) parent);
        }
    }
}