package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.ui.SingleIntegerFieldOptionsPanel;

import javax.swing.*;

public class SwitchStatementWithTooFewBranchesInspection extends StatementInspection {
    private static final int DEFAULT_BRANCH_LIMIT = 2;

    public int m_limit = DEFAULT_BRANCH_LIMIT;  //this is public for the DefaultJDOMExternalizer thingy

    public String getDisplayName() {
        return "Switch statement with too few branches";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    private int getLimit() {
        return m_limit;
    }

    public JComponent createOptionsPanel() {
        return new SingleIntegerFieldOptionsPanel("Minimum number of branches:",
                this, "m_limit");
    }

    protected String buildErrorString(PsiElement location) {
        int branches = 0;
        final PsiSwitchStatement statement = (PsiSwitchStatement) location.getParent();
        final PsiCodeBlock body = statement.getBody();
        final PsiStatement[] statements = body.getStatements();
        for (int i = 0; i < statements.length; i++) {
            final PsiStatement child = statements[i];
            if (child instanceof PsiSwitchLabelStatement) {
                branches++;
            }
        }
        return "'#ref' has too few branches (" + branches + "), and should probably be replaced by an 'if' statement #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SwitchStatementWithTooFewBranchesVisitor(this, inspectionManager, onTheFly);
    }

    private class SwitchStatementWithTooFewBranchesVisitor extends BaseInspectionVisitor {
        private SwitchStatementWithTooFewBranchesVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitSwitchStatement(PsiSwitchStatement statement) {
            int branches = 0;
            final PsiCodeBlock body = statement.getBody();
            if (body == null) {
                return;
            }
            final PsiStatement[] statements = body.getStatements();
            for (int i = 0; i < statements.length; i++) {
                final PsiStatement child = statements[i];
                if (child instanceof PsiSwitchLabelStatement) {
                    branches++;
                }
            }
            if (branches >= getLimit()) {
                return;
            }
            registerStatementError(statement);
        }

    }
}
