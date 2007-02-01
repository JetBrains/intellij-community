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
package com.siyeh.ig.style;

import com.intellij.codeInsight.daemon.GroupNames;
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

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "control.flow.statement.without.braces.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "control.flow.statement.without.braces.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return new ControlFlowStatementFix();
    }

    private static class ControlFlowStatementFix extends InspectionGadgetsFix {

        public String getName() {
            return InspectionGadgetsBundle.message(
                    "control.flow.statement.without.braces.add.quickfix");
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiStatement statement = (PsiStatement)element.getParent();
            @NonNls final String elementText = element.getText();
            final PsiStatement statementWithoutBraces;
            if (statement instanceof PsiDoWhileStatement) {
                final PsiDoWhileStatement doWhileStatement =
                        (PsiDoWhileStatement)statement;
                statementWithoutBraces = doWhileStatement.getBody();
            } else if (statement instanceof PsiForeachStatement) {
                final PsiForeachStatement foreachStatement =
                        (PsiForeachStatement)statement;
                statementWithoutBraces = foreachStatement.getBody();
            } else if (statement instanceof PsiForStatement) {
                final PsiForStatement forStatement = (PsiForStatement)statement;
                statementWithoutBraces = forStatement.getBody();
            } else if (statement instanceof PsiIfStatement) {
                final PsiIfStatement ifStatement = (PsiIfStatement)statement;
                if ("if".equals(elementText)) {
                    statementWithoutBraces = ifStatement.getThenBranch();
                } else {
                    statementWithoutBraces = ifStatement.getElseBranch();
                }
            } else if (statement instanceof PsiWhileStatement) {
                final PsiWhileStatement whileStatement =
                        (PsiWhileStatement)statement;
                statementWithoutBraces = whileStatement.getBody();
            } else {
                assert false;
                statementWithoutBraces = null;
            }
            if (statementWithoutBraces == null) {
                return;
            }
            final String statementText = statementWithoutBraces.getText();
            final PsiElement whiteSpace =
                    statementWithoutBraces.getPrevSibling();
            assert whiteSpace != null;
            replaceStatement(statementWithoutBraces,
                    "{\n" + statementText + "\n}");
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ControlFlowStatementVisitor();
    }

    private static class ControlFlowStatementVisitor
            extends BaseInspectionVisitor {

        public void visitDoWhileStatement(PsiDoWhileStatement statement) {
            super.visitDoWhileStatement(statement);
            final PsiStatement body = statement.getBody();
            if (body == null) {
                return;
            }
            if (!(body instanceof PsiBlockStatement)) {
                final PsiElement doKeyword = statement.getFirstChild();
                if (doKeyword == null) {
                    return;
                }
                registerError(doKeyword);
            }
        }

        public void visitForeachStatement(PsiForeachStatement statement) {
            super.visitForeachStatement(statement);
            final PsiStatement body = statement.getBody();
            if (body == null) {
                return;
            }
            if (!(body instanceof PsiBlockStatement)) {
                final PsiElement forKeyword = statement.getFirstChild();
                if (forKeyword == null) {
                    return;
                }
                registerError(forKeyword);
            }
        }

        public void visitForStatement(PsiForStatement statement) {
            super.visitForStatement(statement);
            final PsiStatement body = statement.getBody();
            if (body == null) {
                return;
            }
            if (!(body instanceof PsiBlockStatement)) {
                final PsiElement forKeyword = statement.getFirstChild();
                if (forKeyword == null) {
                    return;
                }
                registerError(forKeyword);
            }
        }

        public void visitIfStatement(PsiIfStatement statement) {
            super.visitIfStatement(statement);
            final PsiStatement thenBranch = statement.getThenBranch();
            if (thenBranch == null) {
                return;
            }
            if (!(thenBranch instanceof PsiBlockStatement)) {
                final PsiElement ifKeyword = statement.getFirstChild();
                if (ifKeyword == null) {
                    return;
                }
                registerError(ifKeyword);
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

        public void visitWhileStatement(PsiWhileStatement statement) {
            super.visitWhileStatement(statement);
            final PsiStatement body = statement.getBody();
            if (body == null) {
                return;
            }
            if (!(body instanceof PsiBlockStatement)) {
                final PsiElement whileKeyword = statement.getFirstChild();
                if (whileKeyword == null) {
                    return;
                }
                registerError(whileKeyword);
            }
        }
    }
}