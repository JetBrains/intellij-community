/*
 * Copyright 2008 Bas Leijdekkers
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

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class IfMayBeConditionalInspection extends BaseInspection {

    @Override
    @Nls
    @NotNull
    public String getDisplayName() {
        return "If statement could be replaced with simple conditional";
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
        return "<code>#ref</code> could be replaced with simple conditional";
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new IfMayBeConditionalVisitor();
    }

    private static class IfMayBeConditionalVisitor
            extends BaseInspectionVisitor {

        @Override
        public void visitIfStatement(PsiIfStatement statement) {
            super.visitIfStatement(statement);
            final PsiStatement thenBranch = statement.getThenBranch();
            final PsiStatement elseBranch = statement.getElseBranch();
            final PsiStatement thenStatement = getStatement(thenBranch);
            if (thenStatement == null) {
                return;
            }
            final PsiStatement elseStatement = getStatement(elseBranch);
            if (elseStatement == null) {
                return;
            }
            if (thenStatement instanceof PsiReturnStatement) {
                if (!(elseStatement instanceof PsiReturnStatement)) {
                    return;
                }
                registerStatementError(statement);
            } else if (thenStatement instanceof PsiExpressionStatement) {
                if (!(elseStatement instanceof PsiExpressionStatement)) {
                    return;
                }
                final PsiVariable thenVariable =
                        getAssignmentTarget(thenStatement);
                if (thenVariable == null) {
                    return;
                }
                final PsiVariable elseVariable = getAssignmentTarget(
                        elseStatement);
                if (elseVariable == null) {
                    return;
                }
                if (thenVariable != elseVariable) {
                    return;
                }
                registerStatementError(statement);
            }
        }

        private static PsiVariable getAssignmentTarget(PsiStatement statement) {
            final PsiExpressionStatement thenExpressionStatement =
                    (PsiExpressionStatement) statement;

            final PsiExpression expression =
                    thenExpressionStatement.getExpression();
            if (!(expression instanceof PsiAssignmentExpression)) {
                return null;
            }
            final PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression) expression;
            final PsiExpression thenLhs = assignmentExpression.getLExpression();
            if (!(thenLhs instanceof PsiReferenceExpression)) {
                return null;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) thenLhs;
            final PsiElement target = referenceExpression.resolve();
            if (!(target instanceof PsiVariable)) {
                return null;
            }
            return (PsiVariable) target;
        }

        private static PsiStatement getStatement(PsiStatement thenBranch) {
            if (thenBranch instanceof PsiBlockStatement) {
                final PsiBlockStatement blockStatement =
                        (PsiBlockStatement) thenBranch;
                final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
                final PsiStatement[] statements = codeBlock.getStatements();
                if (statements.length != 1) {
                    return null;
                }
                return statements[0];
            } else {
                return thenBranch;
            }
        }
    }
}
