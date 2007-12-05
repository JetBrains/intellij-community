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
package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class WhileLoopSpinsOnFieldInspection extends BaseInspection {

    @SuppressWarnings({"PublicField"})
    public boolean ignoreNonEmtpyLoops = false;

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "while.loop.spins.on.field.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "while.loop.spins.on.field.problem.descriptor");
    }

    @Nullable
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
                "while.loop.spins.on.field.ignore.non.empty.loops.option"),
                this, "ignoreNonEmtpyLoops");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new WhileLoopSpinsOnFieldVisitor();
    }

    private class WhileLoopSpinsOnFieldVisitor
            extends BaseInspectionVisitor {

        @Override public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
            super.visitWhileStatement(statement);
            final PsiStatement body = statement.getBody();
            if (ignoreNonEmtpyLoops && !statementIsEmpty(body)) {
                return;
            }
            final PsiExpression condition = statement.getCondition();
            if (!isSimpleFieldComparison(condition)) {
                return;
            }
            registerStatementError(statement);
        }

        private boolean isSimpleFieldComparison(PsiExpression condition) {
            condition = PsiUtil.deparenthesizeExpression(condition);
            if (condition == null) {
                return false;
            }
            if (isSimpleFieldAccess(condition)) {
                return true;
            }
            if (condition instanceof PsiPrefixExpression) {
                final PsiPrefixExpression prefixExpression =
                        (PsiPrefixExpression) condition;
                final PsiExpression operand =
                        prefixExpression.getOperand();
                return isSimpleFieldComparison(operand);
            }
            if (condition instanceof PsiPostfixExpression) {
                final PsiPostfixExpression postfixExpression =
                        (PsiPostfixExpression) condition;
                final PsiExpression operand =
                        postfixExpression.getOperand();
                return isSimpleFieldComparison(operand);
            }
            if (condition instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression)condition;
                final PsiExpression lOperand = binaryExpression.getLOperand();
                final PsiExpression rOperand = binaryExpression.getROperand();
                if (isLiteral(rOperand)) {
                    return isSimpleFieldComparison(lOperand);
                } else if (isLiteral(lOperand)) {
                    return isSimpleFieldComparison(rOperand);
                } else {
                    return false;
                }
            }
            return false;
        }

        private boolean isLiteral(PsiExpression expression) {
            expression = PsiUtil.deparenthesizeExpression(expression);
            if (expression == null) {
                return false;
            }
            return expression instanceof PsiLiteralExpression;
        }

        private boolean isSimpleFieldAccess(PsiExpression expression) {
            expression = PsiUtil.deparenthesizeExpression(expression);
            if (expression == null) {
                return false;
            }
            if (!(expression instanceof PsiReferenceExpression)) {
                return false;
            }
            final PsiReferenceExpression reference =
                    (PsiReferenceExpression) expression;
            final PsiExpression qualifierExpression =
                    reference.getQualifierExpression();
            if (qualifierExpression != null) {
                return false;
            }
            final PsiElement referent = reference.resolve();
            if (!(referent instanceof PsiField)) {
                return false;
            }
            final PsiField field = (PsiField)referent;
            return !field.hasModifierProperty(PsiModifier.VOLATILE);
        }

        private boolean statementIsEmpty(PsiStatement statement) {
            if (statement == null) {
                return false;
            }
            if (statement instanceof PsiEmptyStatement) {
                return true;
            }
            if (statement instanceof PsiBlockStatement) {
                final PsiBlockStatement blockStatement =
                        (PsiBlockStatement) statement;
                final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
                final PsiStatement[] codeBlockStatements = codeBlock.getStatements();
                for (PsiStatement codeBlockStatement : codeBlockStatements) {
                    if (!statementIsEmpty(codeBlockStatement)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }
    }
}