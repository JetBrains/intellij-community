package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSwitchStatement;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.SwitchUtils;
import com.siyeh.ig.ui.SingleIntegerFieldOptionsPanel;

import javax.swing.*;

public class SwitchStatementWithTooManyBranchesInspection extends StatementInspection {
    private static final int DEFAULT_BRANCH_LIMIT = 10;

    /** @noinspection PublicField*/
    public int m_limit = DEFAULT_BRANCH_LIMIT;  //this is public for the DefaultJDOMExternalizer thingy

    public String getDisplayName() {
        return "'switch' statement with too many branches";
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
        final PsiSwitchStatement statement = (PsiSwitchStatement) location.getParent();
        final int numBranches = SwitchUtils.calculateBranchCount(statement);
        return "'#ref' has too many branches (" + numBranches + ") #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SwitchStatementWithTooManyBranchesVisitor(this, inspectionManager, onTheFly);
    }

    private class SwitchStatementWithTooManyBranchesVisitor extends StatementInspectionVisitor {
        private SwitchStatementWithTooManyBranchesVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitSwitchStatement(PsiSwitchStatement statement) {
            final PsiCodeBlock body = statement.getBody();
            if (body == null) {
                return;
            }
            final int numBranches = SwitchUtils.calculateBranchCount(statement);
            if (numBranches <= getLimit()) {
                return;
            }
            registerStatementError(statement);
        }

    }
}
