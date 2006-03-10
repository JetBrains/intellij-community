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
package com.siyeh.ig.threading;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class WhileLoopSpinsOnFieldInspection extends MethodInspection {

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "while.loop.spins.on.field.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new WhileLoopSpinsOnFieldVisitor();
    }

    private static class WhileLoopSpinsOnFieldVisitor
            extends BaseInspectionVisitor {

        public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
            super.visitWhileStatement(statement);
            final PsiStatement body = statement.getBody();
            if (!statementIsEmpty(body)) {
                return;
            }
            final PsiExpression condition = statement.getCondition();
            if (!isSimpleFieldComparison(condition)) {
                return;
            }
            registerStatementError(statement);
        }

        private static boolean isSimpleFieldComparison(PsiExpression condition) {
            if (condition == null) {
                return false;
            }
            if (isSimpleFieldAccess(condition)) {
                return true;
            }
            if (condition instanceof PsiPrefixExpression) {
                final PsiExpression operand =
                        ((PsiPrefixExpression)condition).getOperand();
                return isSimpleFieldComparison(operand);
            }
            if (condition instanceof PsiPostfixExpression) {
                final PsiExpression operand =
                        ((PsiPostfixExpression)condition).getOperand();
                return isSimpleFieldComparison(operand);
            }
            if (condition instanceof PsiParenthesizedExpression) {
                final PsiExpression operand =
                        ((PsiParenthesizedExpression)condition).getExpression();
                return isSimpleFieldComparison(operand);
            }

            if (condition instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression)condition;
                final PsiExpression lOperand = binaryExpression.getLOperand();
                final PsiExpression rOperand = binaryExpression.getROperand();
                return isSimpleFieldComparison(lOperand) &&
                        isLiteral(rOperand) ||
                        (isSimpleFieldComparison(rOperand) && isLiteral(lOperand));
            }
            return false;
        }

        private static boolean isLiteral(PsiExpression expression) {
            if (expression == null) {
                return false;
            }
            if (expression instanceof PsiParenthesizedExpression) {
                final PsiExpression operand =
                        ((PsiParenthesizedExpression)expression).getExpression();
                return isSimpleFieldAccess(operand);
            }
            return expression instanceof PsiLiteralExpression;
        }

        private static boolean isSimpleFieldAccess(PsiExpression expression) {
            if (expression == null) {
                return false;
            }
            if (expression instanceof PsiParenthesizedExpression) {
                final PsiExpression operand =
                        ((PsiParenthesizedExpression)expression).getExpression();
                return isSimpleFieldAccess(operand);
            }
            if (!(expression instanceof PsiReferenceExpression)) {
                return false;
            }
            final PsiElement referent = ((PsiReference)expression).resolve();
            if (!(referent instanceof PsiField)) {
                return false;
            }
            final PsiField field = (PsiField)referent;
            return !field.hasModifierProperty(PsiModifier.VOLATILE);
        }

        private static boolean statementIsEmpty(PsiStatement statement) {
            if (statement == null) {
                return false;
            }
            if (statement instanceof PsiEmptyStatement) {
                return true;
            }
            if (statement instanceof PsiBlockStatement) {
                final PsiCodeBlock codeBlock =
                        ((PsiBlockStatement)statement).getCodeBlock();
                final PsiStatement[] statements = codeBlock.getStatements();
                for (PsiStatement statement1 : statements) {
                    if (!statementIsEmpty(statement1)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }
    }
}
