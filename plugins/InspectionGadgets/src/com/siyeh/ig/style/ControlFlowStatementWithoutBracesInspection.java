package com.siyeh.ig.style;

import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.codeInsight.daemon.GroupNames;
import org.jetbrains.annotations.Nullable;

public class ControlFlowStatementWithoutBracesInspection extends StatementInspection {

    public String getDisplayName() {
        return "Control flow statement without braces";
    }

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    @Nullable
    protected String buildErrorString(PsiElement location) {
        return "'#ref' without braces #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return new ControlFlowStatementFix();
    }

    private static class ControlFlowStatementFix extends InspectionGadgetsFix {
        public String getName() {
            return "Add braces";
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiStatement statement = (PsiStatement)element.getParent();
            final String elementText = element.getText();
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
            if (statementWithoutBraces.getLastChild() instanceof PsiComment) {
                replaceStatement(statementWithoutBraces, '{' + statementText + "\n}");
            } else {
                replaceStatement(statementWithoutBraces, '{' + statementText + '}');
            }
            final PsiManager psiManager = element.getManager();
            final CodeStyleManager styleManager = psiManager.getCodeStyleManager();
            styleManager.reformat(statement);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ControlFlowStatementVisitor();
    }

    private static class ControlFlowStatementVisitor extends BaseInspectionVisitor {
        public void visitDoWhileStatement(PsiDoWhileStatement statement) {
            super.visitDoWhileStatement(statement);
            final PsiStatement body = statement.getBody();
            if (!(body instanceof PsiBlockStatement)) {
                final PsiElement doKeyword = statement.getFirstChild();
                registerError(doKeyword);
            }
        }

        public void visitForeachStatement(PsiForeachStatement statement) {
            super.visitForeachStatement(statement);
            final PsiStatement body = statement.getBody();
            if (!(body instanceof PsiBlockStatement)) {
                final PsiElement forKeyword = statement.getFirstChild();
                registerError(forKeyword);
            }
        }

        public void visitForStatement(PsiForStatement statement) {
            super.visitForStatement(statement);
            final PsiStatement body = statement.getBody();
            if (!(body instanceof PsiBlockStatement)) {
                final PsiElement forKeyword = statement.getFirstChild();
                registerError(forKeyword);
            }
        }

        public void visitIfStatement(PsiIfStatement statement) {
            super.visitIfStatement(statement);
            final PsiStatement thenBranch = statement.getThenBranch();
            if (!(thenBranch instanceof PsiBlockStatement)) {
                final PsiElement ifKeyword = statement.getFirstChild();
                registerError(ifKeyword);
            }
            final PsiStatement elseBranch = statement.getElseBranch();
            if (!(elseBranch instanceof PsiBlockStatement) &&
                !(elseBranch instanceof PsiIfStatement)) {
                final PsiKeyword elseKeyword = statement.getElseElement();
                registerError(elseKeyword);
            }
        }

        public void visitWhileStatement(PsiWhileStatement statement) {
            super.visitWhileStatement(statement);
            final PsiStatement body = statement.getBody();
            if (!(body instanceof PsiBlockStatement)) {
                final PsiElement whileKeyword = statement.getFirstChild();
                registerError(whileKeyword);
            }
        }
    }
}