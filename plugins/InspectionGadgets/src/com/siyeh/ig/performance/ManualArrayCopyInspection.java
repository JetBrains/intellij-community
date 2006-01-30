/*
 * Copyright 2003-2005 Dave Griffith
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
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ManualArrayCopyInspection extends ExpressionInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "manual.array.copy.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    @Nullable
    protected String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message(
                "manual.array.copy.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ManualArrayCopyVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new ManualArrayCopyFix();
    }

    private static class ManualArrayCopyFix extends InspectionGadgetsFix {

        public String getName() {
            return InspectionGadgetsBundle.message(
                    "manual.array.copy.replace.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement forElement = descriptor.getPsiElement();
            final PsiForStatement forStatement =
                    (PsiForStatement)forElement.getParent();
            final String newExpression = getSystemArrayCopyText(forStatement);
            if (newExpression == null) {
                return;
            }
            replaceStatement(forStatement, newExpression);
        }

        @Nullable
        private static String getSystemArrayCopyText(
                PsiForStatement forStatement) {
            final PsiBinaryExpression condition =
                    (PsiBinaryExpression)forStatement.getCondition();
            if (condition == null) {
                return null;
            }
            final PsiExpression limit = condition.getROperand();
            if (limit == null) {
                return null;
            }
            final String lengthText = limit.getText();

            final PsiStatement initialization =
                    forStatement.getInitialization();
            if (initialization == null) {
                return null;
            }
            if (!(initialization instanceof PsiDeclarationStatement)) {
                return null;
            }
            final PsiDeclarationStatement declaration =
                    (PsiDeclarationStatement)initialization;
            if (declaration.getDeclaredElements().length != 1) {
                return null;
            }
            final PsiLocalVariable var = (PsiLocalVariable)
                    declaration.getDeclaredElements()[0];
            final PsiExpression initialValue = var.getInitializer();
            final PsiExpressionStatement body = getBody(forStatement);
            if (body == null) {
                return null;
            }
            final PsiAssignmentExpression assignment =
                    (PsiAssignmentExpression)body.getExpression();
            final PsiExpression lExpression = assignment.getLExpression();
            final PsiArrayAccessExpression lhs = (PsiArrayAccessExpression)
                    ParenthesesUtils.stripParentheses(lExpression);
            if (lhs == null) {
                return null;
            }
            final PsiExpression lArray = lhs.getArrayExpression();
            final String toArrayText = lArray.getText();
            final PsiExpression rExpression = assignment.getRExpression();
            if (rExpression == null) {
                return null;
            }
            final PsiArrayAccessExpression rhs = (PsiArrayAccessExpression)
                    ParenthesesUtils.stripParentheses(rExpression);
            if (rhs == null) {
                return null;
            }
            final PsiExpression rArray = rhs.getArrayExpression();
            final String fromArrayText = rArray.getText();
            final PsiExpression rhsIndex = rhs.getIndexExpression();
            final int offset = getOffset(initialValue);
            final String fromOffsetText =
                    Integer.toString(offset + getOffset(rhsIndex));
            final PsiExpression lhsIndex = lhs.getIndexExpression();
            final String toOffsetText =
                    Integer.toString(offset + getOffset(lhsIndex));
            @NonNls final StringBuffer buffer =
                    new StringBuffer(25 + fromArrayText.length() +
                            toArrayText.length() + lengthText.length());
            buffer.append("System.arraycopy(");
            buffer.append(fromArrayText);
            buffer.append(", ");
            buffer.append(fromOffsetText);
            buffer.append(", ");
            buffer.append(toArrayText);
            buffer.append(", ");
            buffer.append(toOffsetText);
            buffer.append(", ");
            buffer.append(lengthText);
            buffer.append(");");
            return buffer.toString();
        }

        private static int getOffset(PsiExpression expression) {
            if (PsiUtil.isConstantExpression(expression)) {
                final PsiManager manager = expression.getManager();
                final PsiConstantEvaluationHelper constantEvaluationHelper =
                        manager.getConstantEvaluationHelper();
                final Object result =
                        constantEvaluationHelper.computeConstantExpression(
                                expression);
                if (result instanceof Integer) {
                    final Integer integer = (Integer)result;
                    return integer.intValue();
                } else {
                    return 0;
                }
            } else if (expression instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExp =
                        (PsiBinaryExpression)expression;
                final PsiExpression rhs = binaryExp.getROperand();
                if (rhs == null) {
                    return 0;
                }
                final PsiManager manager = rhs.getManager();
                final PsiConstantEvaluationHelper constantEvaluationHelper =
                        manager.getConstantEvaluationHelper();
                final Object result =
                        constantEvaluationHelper.computeConstantExpression(rhs);
                if (!(result instanceof Integer)) {
                    return 0;
                }
                final Integer integer = (Integer)result;
                final PsiJavaToken sign = binaryExp.getOperationSign();
                final IElementType tokenType = sign.getTokenType();
                if (JavaTokenType.MINUS.equals(tokenType)) {
                    return -integer.intValue();
                } else {
                    return integer.intValue();
                }
            } else {
                return 0;
            }
        }

        @Nullable
        private static PsiExpressionStatement getBody(
                PsiForStatement forStatement) {
            PsiStatement body = forStatement.getBody();
            while (body instanceof PsiBlockStatement) {
                final PsiBlockStatement blockStatement =
                        (PsiBlockStatement)body;
                final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
                final PsiStatement[] statements = codeBlock.getStatements();
                body = statements[0];
            }
            return (PsiExpressionStatement)body;
        }
    }

    private static class ManualArrayCopyVisitor extends BaseInspectionVisitor {

        public void visitForStatement(@NotNull PsiForStatement forStatement) {
            super.visitForStatement(forStatement);
            final PsiStatement initialization =
                    forStatement.getInitialization();
            if (!(initialization instanceof PsiDeclarationStatement)) {
                return;
            }
            final PsiDeclarationStatement declaration =
                    (PsiDeclarationStatement)initialization;
            if (declaration.getDeclaredElements().length != 1) {
                return;
            }
            final PsiLocalVariable var = (PsiLocalVariable)
                    declaration.getDeclaredElements()[0];
            final PsiExpression initialValue = var.getInitializer();
            if (initialValue != null) {
                final PsiManager manager = initialValue.getManager();
                final PsiConstantEvaluationHelper constantEvaluationHelper =
                        manager.getConstantEvaluationHelper();
                final Object value =
                        constantEvaluationHelper.computeConstantExpression(
                                initialValue);
                if (!(value instanceof Integer)) {
                    return;
                }
                final Integer integer = (Integer)value;
                if (integer.intValue() != 0) {
                    return;
                }
            }
            final PsiExpression condition = forStatement.getCondition();
            if (!isComparison(condition, var)) {
                return;
            }
            final PsiStatement update = forStatement.getUpdate();
            if (!isIncrement(update, var)) {
                return;
            }
            final PsiStatement body = forStatement.getBody();
            if (!bodyIsArrayMove(body, var)) {
                return;
            }
            registerStatementError(forStatement);
        }

        private static boolean bodyIsArrayMove(PsiStatement body,
                                               PsiLocalVariable var) {
            if (body instanceof PsiExpressionStatement) {
                final PsiExpressionStatement exp =
                        (PsiExpressionStatement)body;
                final PsiExpression expression = exp.getExpression();
                return expressionIsArrayMove(expression, var);
            }
            else if (body instanceof PsiBlockStatement) {
                final PsiBlockStatement blockStatement =
                        (PsiBlockStatement)body;
                final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
                final PsiStatement[] statements = codeBlock.getStatements();
                if (statements.length != 1) {
                    return false;
                }
                return bodyIsArrayMove(statements[0], var);
            }
            return false;
        }

        private static boolean expressionIsArrayMove(PsiExpression exp,
                                                     PsiLocalVariable var) {
            final PsiExpression strippedExpression =
                    ParenthesesUtils.stripParentheses(exp);
            if (strippedExpression == null) {
                return false;
            }
            if (!(strippedExpression instanceof PsiAssignmentExpression)) {
                return false;
            }
            final PsiAssignmentExpression assignment =
                    (PsiAssignmentExpression)strippedExpression;
            final PsiJavaToken sign = assignment.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.EQ)) {
                return false;
            }
            final PsiExpression lhs = assignment.getLExpression();
            if (SideEffectChecker.mayHaveSideEffects(lhs)) {
                return false;
            }
            if (!isOffsetArrayAccess(lhs, var)) {
                return false;
            }
            final PsiExpression rhs = assignment.getRExpression();
            if (rhs == null) {
                return false;
            }
            if (SideEffectChecker.mayHaveSideEffects(rhs)) {
                return false;
            }
            final PsiType lhsType = lhs.getType();
            if (lhsType == null) {
                return false;
            }
            final PsiType rhsType = rhs.getType();
            if (rhsType == null) {
                // fix for NullPointerException in PsiArrayType.equals()
                // when the parameter is null in #4055
                return false;
            }
            if (!lhsType.equals(rhsType)) {
                return false;
            }
            return isOffsetArrayAccess(rhs, var);
        }

        private static boolean isOffsetArrayAccess(PsiExpression expression,
                                                   PsiLocalVariable var) {
            final PsiExpression strippedExpression =
                    ParenthesesUtils.stripParentheses(expression);
            if (!(strippedExpression instanceof PsiArrayAccessExpression)) {
                return false;
            }
            final PsiArrayAccessExpression arrayExp =
                    (PsiArrayAccessExpression)strippedExpression;
            final PsiExpression index = arrayExp.getIndexExpression();
            if (index == null) {
                return false;
            }
            return expressionIsOffsetVariableLookup(index, var);
        }

        private static boolean isIncrement(PsiStatement statement,
                                           PsiLocalVariable var) {
            if (!(statement instanceof PsiExpressionStatement)) {
                return false;
            }
            final PsiExpressionStatement expressionStatement =
                    (PsiExpressionStatement)statement;
            PsiExpression exp = expressionStatement.getExpression();
            exp = ParenthesesUtils.stripParentheses(exp);
            if (exp instanceof PsiPrefixExpression) {
                final PsiPrefixExpression prefixExp = (PsiPrefixExpression)exp;
                final PsiJavaToken sign = prefixExp.getOperationSign();
                final IElementType tokenType = sign.getTokenType();
                if (!tokenType.equals(JavaTokenType.PLUSPLUS)) {
                    return false;
                }
                final PsiExpression operand = prefixExp.getOperand();
                return expressionIsVariableLookup(operand, var);
            }
            else if (exp instanceof PsiPostfixExpression) {
                final PsiPostfixExpression postfixExp =
                        (PsiPostfixExpression)exp;
                final PsiJavaToken sign = postfixExp.getOperationSign();
                final IElementType tokenType = sign.getTokenType();
                if (!tokenType.equals(JavaTokenType.PLUSPLUS)) {
                    return false;
                }
                final PsiExpression operand = postfixExp.getOperand();
                return expressionIsVariableLookup(operand, var);
            }
            return true;
        }

        private static boolean isComparison(PsiExpression condition,
                                            PsiLocalVariable var) {
            final PsiExpression strippedCondition =
                    ParenthesesUtils.stripParentheses(condition);

            if (!(strippedCondition instanceof PsiBinaryExpression)) {
                return false;
            }
            final PsiBinaryExpression binaryExp =
                    (PsiBinaryExpression)strippedCondition;
            final PsiJavaToken sign = binaryExp.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.LT)) {
                return false;
            }
            final PsiExpression lhs = binaryExp.getLOperand();
            return expressionIsVariableLookup(lhs, var);
        }

        private static boolean expressionIsVariableLookup(
                PsiExpression expression, PsiLocalVariable var) {
            final PsiExpression strippedExpression =
                    ParenthesesUtils.stripParentheses(expression);
            if (strippedExpression == null) {
                return false;
            }
            final String expressionText = strippedExpression.getText();
            final String varText = var.getName();
            return expressionText.equals(varText);
        }

        private static boolean expressionIsOffsetVariableLookup(
                PsiExpression expression, PsiLocalVariable var) {
            final PsiExpression strippedExpression =
                    ParenthesesUtils.stripParentheses(expression);
            if (expressionIsVariableLookup(strippedExpression, var)) {
                return true;
            }
            if (!(strippedExpression instanceof PsiBinaryExpression)) {
                return false;
            }
            final PsiBinaryExpression binaryExp =
                    (PsiBinaryExpression)strippedExpression;
            final PsiExpression lhs = binaryExp.getLOperand();
            if (!expressionIsVariableLookup(lhs, var)) {
                return false;
            }
            final PsiJavaToken sign = binaryExp.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            return !(!tokenType.equals(JavaTokenType.PLUS) &&
                    !tokenType.equals(JavaTokenType.MINUS));
        }
    }
}