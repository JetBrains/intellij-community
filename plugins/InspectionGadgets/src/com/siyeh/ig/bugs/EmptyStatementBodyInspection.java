package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;

import javax.swing.*;

public class EmptyStatementBodyInspection extends StatementInspection {
    public boolean m_reportEmptyBlocks = false;

    public String getID(){
        return "StatementWithEmptyBody";
    }

    public String getDisplayName() {
        return "Statement with empty body";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }
    public String buildErrorString(PsiElement location) {
        return "#ref statement has empty body #loc";
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Include statement bodies that are empty code blocks",
                this, "m_reportEmptyBlocks");
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new EmptyStatementVisitor(this, inspectionManager, onTheFly);
    }

    private class EmptyStatementVisitor extends BaseInspectionVisitor {
        private EmptyStatementVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitDoWhileStatement(PsiDoWhileStatement statement) {
            super.visitDoWhileStatement(statement);
            final PsiStatement body = statement.getBody();
            if (body == null) {
                return;
            }
            if (!isEmpty(body)) {
                return;
            }
            registerStatementError(statement);
        }

        public void visitWhileStatement(PsiWhileStatement statement) {
            super.visitWhileStatement(statement);
            final PsiStatement body = statement.getBody();
            if (body == null) {
                return;
            }
            if (!isEmpty(body)) {
                return;
            }
            registerStatementError(statement);
        }

        public void visitForStatement(PsiForStatement statement) {
            super.visitForStatement(statement);

            final PsiStatement body = statement.getBody();
            if (body == null) {
                return;
            }
            if (!isEmpty(body)) {
                return;
            }
            registerStatementError(statement);
        }

        public void visitForeachStatement(PsiForeachStatement statement) {
            super.visitForeachStatement(statement);

            final PsiStatement body = statement.getBody();
            if (body == null) {
                return;
            }
            if (!isEmpty(body)) {
                return;
            }
            registerStatementError(statement);
        }

        public void visitIfStatement(PsiIfStatement statement) {
            super.visitIfStatement(statement);
            final PsiStatement thenBranch = statement.getThenBranch();
            if (thenBranch != null) {
                if (isEmpty(thenBranch)) {
                    registerStatementError(statement);
                    return;
                }
            }
            final PsiStatement elseBranch = statement.getElseBranch();

            if (elseBranch != null) {
                if (isEmpty(elseBranch)) {
                    final PsiElement elseToken = statement.getElseElement();
                    registerError(elseToken);
                }
            }
        }

        private boolean isEmpty(PsiElement body) {
            if (body instanceof PsiEmptyStatement) {
                return true;
            } else if (m_reportEmptyBlocks && body instanceof PsiBlockStatement) {
                final PsiBlockStatement block = (PsiBlockStatement) body;
                final PsiCodeBlock codeBlock = block.getCodeBlock();
                return codeBlockIsEmpty(codeBlock);
            } else if (body instanceof PsiCodeBlock) {
                final PsiCodeBlock codeBlock = (PsiCodeBlock) body;
                return codeBlockIsEmpty(codeBlock);
            }
            return false;
        }

        private boolean codeBlockIsEmpty(PsiCodeBlock codeBlock) {
            final PsiStatement[] statements = codeBlock.getStatements();
            return statements.length == 0;
        }
    }
}
