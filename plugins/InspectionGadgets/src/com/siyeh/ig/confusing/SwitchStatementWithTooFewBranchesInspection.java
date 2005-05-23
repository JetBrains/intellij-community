package com.siyeh.ig.confusing;

import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSwitchStatement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.psiutils.SwitchUtils;
import com.siyeh.ig.ui.SingleIntegerFieldOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SwitchStatementWithTooFewBranchesInspection extends StatementInspection {
    private static final int DEFAULT_BRANCH_LIMIT = 2;

    /** @noinspection PublicField*/
    public int m_limit = DEFAULT_BRANCH_LIMIT;  //this is public for the DefaultJDOMExternalizer thingy

    public String getDisplayName() {
        return "'switch' statement with too few branches";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    private int getLimit() {
        return m_limit;
    }

    public JComponent createOptionsPanel() {
        return new SingleIntegerFieldOptionsPanel("Minimum number of branches:",
                this, "m_limit");
    }

    protected String buildErrorString(PsiElement location) {
        final PsiSwitchStatement statement = (PsiSwitchStatement) location.getParent();
        final int numBranches = SwitchUtils.calculateBranchCount(statement);
        return "'#ref' has too few branches (" + numBranches + "), and should probably be replaced by an 'if' statement #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SwitchStatementWithTooFewBranchesVisitor();
    }

    private class SwitchStatementWithTooFewBranchesVisitor extends StatementInspectionVisitor {

        public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
            final PsiCodeBlock body = statement.getBody();
            if (body == null) {
                return;
            }
            final int numBranches = SwitchUtils.calculateBranchCount(statement);
            if (numBranches >= getLimit()) {
                return;
            }
            registerStatementError(statement);
        }
    }

}
