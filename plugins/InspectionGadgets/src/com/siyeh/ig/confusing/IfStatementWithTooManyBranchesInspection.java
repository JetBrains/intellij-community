package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiStatement;
import com.siyeh.ig.*;
import com.siyeh.ig.ui.SingleIntegerFieldOptionsPanel;

import javax.swing.*;

public class IfStatementWithTooManyBranchesInspection extends StatementInspection {
    private static final int DEFAULT_BRANCH_LIMIT = 3;

    /** @noinspection PublicField*/
    public int m_limit = DEFAULT_BRANCH_LIMIT;  //this is public for the DefaultJDOMExternalizer thingy

    public String getDisplayName() {
        return "'if' statement with too many branches";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    private int getLimit() {
        return m_limit;
    }

    public JComponent createOptionsPanel() {
        return new SingleIntegerFieldOptionsPanel("Maximum number of branches:",
                this, "m_limit");
    }

    protected String buildErrorString(PsiElement location) {
        final PsiIfStatement statement = (PsiIfStatement) location.getParent();
        final int branches = calculateNumBranches(statement);
        return "'#ref' has too many branches (" + branches + ") #loc";
    }

    private int calculateNumBranches(PsiIfStatement statement) {
        final PsiStatement branch = statement.getElseBranch();
        if (branch == null) {
            return 1;
        }
        if (!(branch instanceof PsiIfStatement)) {
            return 2;
        }
        return 1 + calculateNumBranches((PsiIfStatement) branch);
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new IfStatementWithTooManyBranchesVisitor(this, inspectionManager, onTheFly);
    }

    private class IfStatementWithTooManyBranchesVisitor extends StatementInspectionVisitor {
        private IfStatementWithTooManyBranchesVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitIfStatement(PsiIfStatement statement) {
            super.visitIfStatement(statement);
            final PsiElement parent = statement.getParent();
            if (parent instanceof PsiIfStatement) {
                final PsiIfStatement parentStatement = (PsiIfStatement) parent;
                final PsiStatement elseBranch = parentStatement.getElseBranch();
                if (statement.equals(elseBranch)) {
                    return;
                }
            }
            final int branches = calculateNumBranches(statement);
            if (branches <= getLimit()) {
                return;
            }
            registerStatementError(statement);
        }

    }
}
