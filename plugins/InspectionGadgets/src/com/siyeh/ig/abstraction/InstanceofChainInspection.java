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
package com.siyeh.ig.abstraction;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class InstanceofChainInspection extends BaseInspection {

    @NotNull
    public String getID(){
        return "ChainOfInstanceofChecks";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "chain.of.instanceof.checks.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "chain.of.instanceof.checks.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new InstanceofChainVisitor();
    }

    private static class InstanceofChainVisitor
            extends BaseInspectionVisitor {

        public void visitIfStatement(@NotNull PsiIfStatement statement) {
            super.visitIfStatement(statement);
            final PsiElement parent = statement.getParent();
            if (parent instanceof PsiIfStatement) {
                final PsiIfStatement parentStatement = (PsiIfStatement) parent;
                final PsiStatement elseBranch = parentStatement.getElseBranch();
                if (statement.equals(elseBranch)) {
                    return;
                }
            }
            int numChecks = 0;
            PsiIfStatement branch = statement;
            while (branch != null) {
                final PsiExpression condition = branch.getCondition();
                if (!isInstanceofCheck(condition)) {
                    return;
                }
                numChecks++;
                final PsiStatement elseBranch = branch.getElseBranch();
                if (elseBranch instanceof PsiIfStatement) {
                    branch = (PsiIfStatement) elseBranch;
                } else {
                    branch = null;
                }
            }
            if (numChecks < 2) {
                return;
            }
            registerStatementError(statement);
        }

        private static boolean isInstanceofCheck(PsiExpression condition) {
            if (condition == null) {
                return false;
            } else if (condition instanceof PsiInstanceOfExpression) {
                return true;
            } else if (condition instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression) condition;
                final PsiExpression lhs = binaryExpression.getLOperand();
                final PsiExpression rhs = binaryExpression.getROperand();
                return isInstanceofCheck(lhs) && isInstanceofCheck(rhs);
            } else if (condition instanceof PsiParenthesizedExpression) {
                final PsiParenthesizedExpression parenthesizedExpression =
                        (PsiParenthesizedExpression)condition;
                final PsiExpression contents =
                        parenthesizedExpression.getExpression();
                return isInstanceofCheck(contents);
            } else if (condition instanceof PsiPrefixExpression) {
                final PsiPrefixExpression prefixExpression =
                        (PsiPrefixExpression)condition;
                final PsiExpression contents = prefixExpression.getOperand();
                return isInstanceofCheck(contents);
            } else if (condition instanceof PsiPostfixExpression) {
                final PsiPostfixExpression postfixExpression =
                        (PsiPostfixExpression)condition;
                final PsiExpression contents = postfixExpression.getOperand();
                return isInstanceofCheck(contents);
            }
            return false;
        }
    }
}