/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ManualArrayCopyInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "manual.array.copy.display.name");
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
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

        @NotNull
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
                PsiForStatement forStatement)
                throws IncorrectOperationException {
            final PsiExpression condition = forStatement.getCondition();
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression)PsiUtil.deparenthesizeExpression(
                            condition);
            if (binaryExpression == null) {
                return null;
            }
            final PsiExpression limit;
            if (JavaTokenType.LT.equals(
                    binaryExpression.getOperationTokenType()))  {
                limit = binaryExpression.getROperand();
            } else {
                limit = binaryExpression.getLOperand();
            }
            if (limit == null) {
                return null;
            }
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
            final PsiLocalVariable variable = (PsiLocalVariable)
                    declaration.getDeclaredElements()[0];
            final String lengthText = getLengthText(limit, variable);
            final PsiExpressionStatement body = getBody(forStatement);
            if (body == null) {
                return null;
            }
            final PsiAssignmentExpression assignment =
                    (PsiAssignmentExpression)body.getExpression();
            final PsiExpression lExpression = assignment.getLExpression();
            final PsiArrayAccessExpression lhs = (PsiArrayAccessExpression)
                    PsiUtil.deparenthesizeExpression(lExpression);
            if (lhs == null) {
                return null;
            }
            final PsiExpression lArray = lhs.getArrayExpression();
            final String toArrayText = lArray.getText();
            final PsiExpression rExpression = assignment.getRExpression();
            final PsiArrayAccessExpression rhs = (PsiArrayAccessExpression)
                    PsiUtil.deparenthesizeExpression(rExpression);
            if (rhs == null) {
                return null;
            }
            final PsiExpression rArray = rhs.getArrayExpression();
            final String fromArrayText = rArray.getText();
            final PsiExpression rhsIndexExpression = rhs.getIndexExpression();
            final PsiExpression strippedRhsIndexExpression =
                    PsiUtil.deparenthesizeExpression(rhsIndexExpression);
            final String fromOffsetText =
                    getOffsetText(strippedRhsIndexExpression, variable);
            final PsiExpression lhsIndexExpression = lhs.getIndexExpression();
            final PsiExpression strippedLhsIndexExpression =
                    PsiUtil.deparenthesizeExpression(lhsIndexExpression);
            final String toOffsetText =
                    getOffsetText(strippedLhsIndexExpression, variable);
            @NonNls final StringBuilder buffer = new StringBuilder(60);
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

        @NonNls @Nullable
        private static String getLengthText(PsiExpression expression,
                                            PsiLocalVariable variable) {
            expression =
                    PsiUtil.deparenthesizeExpression(expression);
            if (expression == null) {
                return null;
            }
            final PsiExpression initializer = variable.getInitializer();
            final String expressionText = expression.getText();
            if (initializer == null) {
                return expressionText;
            }
            if (ExpressionUtils.isZero(initializer)) {
                return expressionText;
            }
            return expressionText + '-' + initializer.getText();
        }

        @NonNls @Nullable
        private static String getOffsetText(PsiExpression expression,
                                            PsiLocalVariable variable)
                throws IncorrectOperationException {
            if (expression == null) {
                return null;
            }
            final String expressionText = expression.getText();
            final String variableName = variable.getName();
            if (expressionText.equals(variableName)) {
                final PsiExpression initialValue = variable.getInitializer();
                if (initialValue == null) {
                    return null;
                }
                return initialValue.getText();
            }
            if (expression instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression)expression;
                final PsiExpression lhs = binaryExpression.getLOperand();
                final String lhsText = getOffsetText(lhs, variable);
                final PsiExpression rhs = binaryExpression.getROperand();
                final String rhsText = getOffsetText(rhs, variable);
                final PsiJavaToken sign = binaryExpression.getOperationSign();
                final IElementType tokenType = sign.getTokenType();
                if (lhsText == null || lhsText.equals("0")) {
                    if (tokenType.equals(JavaTokenType.MINUS)) {
                        return '-' + rhsText;
                    }
                    return rhsText;
                }
                if (rhsText == null || rhsText.equals("0")) {
                    return lhsText;
                }
                return collapseConstant(lhsText + sign.getText() + rhsText,
                        variable);
            }
            return collapseConstant(expression.getText(), variable);
        }

        private static String collapseConstant(@NonNls String expressionText,
                                               PsiElement context)
                throws IncorrectOperationException {
            final PsiManager manager = context.getManager();
            final PsiElementFactory factory = manager.getElementFactory();
            final PsiConstantEvaluationHelper evaluationHelper =
                    manager.getConstantEvaluationHelper();
            final PsiExpression fromOffsetExpression =
                    factory.createExpressionFromText(expressionText, context);
            final Object fromOffsetConstant =
                    evaluationHelper.computeConstantExpression(
                            fromOffsetExpression);
            if (fromOffsetConstant != null) {
                return fromOffsetConstant.toString();
            } else {
                return expressionText;
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

        public void visitForStatement(@NotNull PsiForStatement statement) {
            super.visitForStatement(statement);
            final PsiStatement initialization =
                    statement.getInitialization();
            if (!(initialization instanceof PsiDeclarationStatement)) {
                return;
            }
            final PsiDeclarationStatement declaration =
                    (PsiDeclarationStatement)initialization;
            if (declaration.getDeclaredElements().length != 1) {
                return;
            }
            final PsiLocalVariable variable = (PsiLocalVariable)
                    declaration.getDeclaredElements()[0];
            final PsiExpression initialValue = variable.getInitializer();
            if (initialValue == null) {
                return;
            }
            final PsiExpression condition = statement.getCondition();
            if (!ExpressionUtils.isComparison(condition, variable)) {
                return;
            }
            final PsiStatement update = statement.getUpdate();
            if (!VariableAccessUtils.variableIsIncremented(variable, update)) {
                return;
            }
            final PsiStatement body = statement.getBody();
            if (!bodyIsArrayCopy(body, variable)) {
                return;
            }
            registerStatementError(statement);
        }

        private static boolean bodyIsArrayCopy(PsiStatement body,
                                               PsiLocalVariable variable) {
            if (body instanceof PsiExpressionStatement) {
                final PsiExpressionStatement exp =
                        (PsiExpressionStatement)body;
                final PsiExpression expression = exp.getExpression();
                return expressionIsArrayCopy(expression, variable);
            } else if (body instanceof PsiBlockStatement) {
                final PsiBlockStatement blockStatement =
                        (PsiBlockStatement)body;
                final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
                final PsiStatement[] statements = codeBlock.getStatements();
                return statements.length == 1 &&
                        bodyIsArrayCopy(statements[0], variable);
            }
            return false;
        }

        private static boolean expressionIsArrayCopy(
                @Nullable PsiExpression expression,
                @NotNull PsiLocalVariable variable) {
            final PsiExpression strippedExpression =
                    PsiUtil.deparenthesizeExpression(expression);
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
            if (!ExpressionUtils.isOffsetArrayAccess(lhs, variable)) {
                return false;
            }
            final PsiExpression rhs = assignment.getRExpression();
            if (rhs == null) {
                return false;
            }
            if (SideEffectChecker.mayHaveSideEffects(rhs)) {
                return false;
            }
            if (!areExpressionsCopyable(lhs, rhs)) {
                return false;
            }
            final PsiType type = lhs.getType();
            if (type instanceof PsiPrimitiveType) {
                final PsiExpression strippedLhs =
                        ParenthesesUtils.stripParentheses(lhs);
                final PsiExpression strippedRhs =
                        ParenthesesUtils.stripParentheses(rhs);
                if (!areExpressionsCopyable(strippedLhs, strippedRhs)) {
                    return false;
                }
            }
            return ExpressionUtils.isOffsetArrayAccess(rhs, variable);
        }

        private static boolean areExpressionsCopyable(
                @Nullable PsiExpression lhs, @Nullable PsiExpression rhs) {
            if (lhs == null || rhs == null) {
                return false;
            }
            final PsiType lhsType = lhs.getType();
            if (lhsType == null) {
                return false;
            }
            final PsiType rhsType = rhs.getType();
            if (rhsType == null) {
                return false;
            }
            if (lhsType instanceof PsiPrimitiveType) {
                if (!lhsType.equals(rhsType)) {
                    return false;
                }
            } else {
                if (!lhsType.isAssignableFrom(rhsType)) {
                    return false;
                }
            }
            return true;
        }
    }
}