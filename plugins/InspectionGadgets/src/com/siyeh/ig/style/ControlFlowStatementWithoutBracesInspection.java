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
package com.siyeh.ig.style;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.StatementInspection;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

public class ControlFlowStatementWithoutBracesInspection extends StatementInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("control.flow.statement.without.braces.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    @Nullable
    protected String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message("control.flow.statement.without.braces.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return new ControlFlowStatementFix();
    }

    private static class ControlFlowStatementFix extends InspectionGadgetsFix {
        public String getName() {
            return InspectionGadgetsBundle.message("control.flow.statement.without.braces.add.quickfix");
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiStatement statement = (PsiStatement)element.getParent();
            @NonNls final String elementText = element.getText();
            final String statementText;
            final PsiStatement statementWithoutBraces;
            if (statement instanceof PsiDoWhileStatement) {
                final PsiDoWhileStatement doWhileStatement = (PsiDoWhileStatement)statement;
                final PsiStatement body = doWhileStatement.getBody();
                statementText = body.getText();
                statementWithoutBraces = body;
            } else if (statement instanceof PsiForeachStatement) {
                final PsiForeachStatement foreachStatement = (PsiForeachStatement)statement;
                final PsiStatement body = foreachStatement.getBody();
                statementText = body.getText();
                statementWithoutBraces = body;
            } else if (statement instanceof PsiForStatement) {
                final PsiForStatement forStatement = (PsiForStatement)statement;
                final PsiStatement body = forStatement.getBody();
                statementText = body.getText();
                statementWithoutBraces = body;
            } else if (statement instanceof PsiIfStatement) {
                final PsiIfStatement ifStatement = (PsiIfStatement)statement;
                if ("if".equals(elementText)) {
                    final PsiStatement thenBranch = ifStatement.getThenBranch();
                    statementText = thenBranch.getText();
                    statementWithoutBraces = thenBranch;
                } else {
                    final PsiStatement elseBranch = ifStatement.getElseBranch();
                    statementText = elseBranch.getText();
                    statementWithoutBraces = elseBranch;
                }
            } else if (statement instanceof PsiWhileStatement) {
                final PsiWhileStatement whileStatement = (PsiWhileStatement)statement;
                final PsiStatement body = whileStatement.getBody();
                statementText = body.getText();
                statementWithoutBraces = body;
            } else {
                assert false;
                statementText = null;
                statementWithoutBraces = null;
            }
            final PsiElement whiteSpace = statementWithoutBraces.getPrevSibling();
            assert whiteSpace != null;
            final String whiteSpaceText = whiteSpace.getText();
            final String leftBrace;
            if (whiteSpaceText.indexOf('\n') >= 0) {
                leftBrace = "{\n";
            } else {
                leftBrace = "{";
            }
            final String rightBrace;
            if (statementWithoutBraces.getLastChild() instanceof PsiComment) {
                rightBrace = "\n}";
            } else {
                rightBrace = "}";
            }
            replaceStatement(statementWithoutBraces, leftBrace + statementText + rightBrace);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ControlFlowStatementVisitor();
    }

    private static class ControlFlowStatementVisitor extends BaseInspectionVisitor {
        public void visitDoWhileStatement(PsiDoWhileStatement statement) {
            super.visitDoWhileStatement(statement);
            final PsiStatement body = statement.getBody();
            if (body == null) {
                return;
            }
            if (!(body instanceof PsiBlockStatement)) {
                final PsiElement doKeyword = statement.getFirstChild();
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
                registerError(ifKeyword);
            }
            final PsiStatement elseBranch = statement.getElseBranch();
            if (elseBranch == null) {
                return;
            }
            if (!(elseBranch instanceof PsiBlockStatement) &&
                !(elseBranch instanceof PsiIfStatement)) {
                final PsiKeyword elseKeyword = statement.getElseElement();
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
                registerError(whileKeyword);
            }
        }
    }
}