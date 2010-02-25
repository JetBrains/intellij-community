/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ControlFlowStatementWithoutBracesInspection
        extends BaseInspection {

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "control.flow.statement.without.braces.display.name");
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "control.flow.statement.without.braces.problem.descriptor");
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos){
        return new ControlFlowStatementFix();
    }

    private static class ControlFlowStatementFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "control.flow.statement.without.braces.add.quickfix");
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement parent = element.getParent();
            if (!(parent instanceof PsiStatement)) {
                return;
            }
            final PsiStatement statement = (PsiStatement)parent;
            @NonNls final String elementText = element.getText();
            final PsiStatement statementWithoutBraces;
            if (statement instanceof PsiLoopStatement) {
                final PsiLoopStatement loopStatement =
                        (PsiLoopStatement) statement;
                statementWithoutBraces = loopStatement.getBody();
            } else if (statement instanceof PsiIfStatement) {
                final PsiIfStatement ifStatement = (PsiIfStatement)statement;
                if ("if".equals(elementText)) {
                    statementWithoutBraces = ifStatement.getThenBranch();
                    if (statementWithoutBraces == null) {
                        return;
                    }
                    final PsiElement nextSibling =
                            statementWithoutBraces.getNextSibling();
                    if (nextSibling instanceof PsiWhiteSpace) {
                        // to avoid "else" on new line
                        nextSibling.delete();
                    }
                } else {
                    statementWithoutBraces = ifStatement.getElseBranch();
                }
            } else {
                return;
            }
            if (statementWithoutBraces == null) {
                return;
            }
            final String newStatementText =
                    "{\n" + statementWithoutBraces.getText() + "\n}";
            replaceStatement(statementWithoutBraces, newStatementText);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ControlFlowStatementVisitor();
    }

    private static class ControlFlowStatementVisitor
            extends BaseInspectionVisitor {

        @Override public void visitDoWhileStatement(PsiDoWhileStatement statement) {
            super.visitDoWhileStatement(statement);
            final PsiStatement body = statement.getBody();
            if (body == null || body instanceof PsiBlockStatement) {
                return;
            }
            registerStatementError(statement);
        }

        @Override public void visitForeachStatement(PsiForeachStatement statement) {
            super.visitForeachStatement(statement);
            final PsiStatement body = statement.getBody();
            if (body == null || body instanceof PsiBlockStatement) {
                return;
            }
            registerStatementError(statement);
        }

        @Override public void visitForStatement(PsiForStatement statement) {
            super.visitForStatement(statement);
            final PsiStatement body = statement.getBody();
            if (body == null || body instanceof PsiBlockStatement) {
                return;
            }
            registerStatementError(statement);
        }

        @Override public void visitIfStatement(PsiIfStatement statement) {
            super.visitIfStatement(statement);
            final PsiStatement thenBranch = statement.getThenBranch();
            if (thenBranch == null) {
                return;
            }
            if (!(thenBranch instanceof PsiBlockStatement)) {
                registerStatementError(statement);
            }
            final PsiStatement elseBranch = statement.getElseBranch();
            if (elseBranch == null) {
                return;
            }
            if (!(elseBranch instanceof PsiBlockStatement) &&
                !(elseBranch instanceof PsiIfStatement)) {
                final PsiKeyword elseKeyword = statement.getElseElement();
                if (elseKeyword == null) {
                    return;
                }
                registerError(elseKeyword);
            }
        }

        @Override public void visitWhileStatement(PsiWhileStatement statement) {
            super.visitWhileStatement(statement);
            final PsiStatement body = statement.getBody();
            if (body == null || body instanceof PsiBlockStatement) {
                return;
            }
            registerStatementError(statement);
        }
    }
}