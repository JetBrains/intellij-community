package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.ui.SingleIntegerFieldOptionsPanel;

import javax.swing.*;

public class SwitchStatementWithTooManyBranchesInspection extends StatementInspection {
    private static final int DEFAULT_BRANCH_LIMIT = 10;

    public int m_limit = DEFAULT_BRANCH_LIMIT;  //this is public for the DefaultJDOMExternalizer thingy

    public String getDisplayName() {
        return "Switch statement with too many branches";
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
        return "'#ref' has too many branches (" + branches + ") #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SwitchStatementWithTooManyBranchesVisitor(this, inspectionManager, onTheFly);
    }

    private class SwitchStatementWithTooManyBranchesVisitor extends BaseInspectionVisitor {
        private SwitchStatementWithTooManyBranchesVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
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
            if (branches <= getLimit()) {
                return;
            }
            registerStatementError(statement);
        }

    }
}
