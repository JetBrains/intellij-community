package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.psiutils.SwitchUtils;
import com.siyeh.ig.ui.SingleIntegerFieldOptionsPanel;

import javax.swing.*;

public class SwitchStatementDensityInspection extends StatementInspection {
    private static final int DEFAULT_DENSITY_LIMIT = 20;

    /** @noinspection PublicField*/
    public int m_limit = DEFAULT_DENSITY_LIMIT;  //this is public for the DefaultJDOMExternalizer thingy

    public String getDisplayName() {
        return "Switch statement with too low of branch density";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    private int getLimit() {
        return m_limit;
    }

    public JComponent createOptionsPanel() {
        return new SingleIntegerFieldOptionsPanel("Minimum density of branches: %",
                this, "m_limit");
    }

    protected String buildErrorString(PsiElement location) {
        final PsiSwitchStatement statement = (PsiSwitchStatement) location.getParent();
        final double density = calculateDensity(statement);
        final int intDensity = (int) (density * 100.0);
        return "'#ref' has too low of branch density (" + intDensity + "%) #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SwitchStatementWithTooFewBranchesVisitor(this, inspectionManager, onTheFly);
    }

    private class SwitchStatementWithTooFewBranchesVisitor extends BaseInspectionVisitor {
        private SwitchStatementWithTooFewBranchesVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitSwitchStatement(PsiSwitchStatement statement) {
            final PsiCodeBlock body = statement.getBody();
            if (body == null) {
                return;
            }
            final double density = calculateDensity(statement);
            if (density * 100.0 > getLimit()) {
                return;
            }
            registerStatementError(statement);
        }
    }

    private static double calculateDensity(PsiSwitchStatement statement) {
        final PsiCodeBlock body = statement.getBody();
        final int numBranches = SwitchUtils.calculateBranchCount(statement);
        final StatementCountVisitor visitor = new StatementCountVisitor();
        body.accept(visitor);
        final int numStatements = visitor.getNumStatements();
        return (double) numBranches / (double) numStatements;
    }

    private static class StatementCountVisitor extends PsiRecursiveElementVisitor {
        private int numStatements = 0;

        public void visitStatement(PsiStatement psiStatement) {
            super.visitStatement(psiStatement);
            numStatements++;
        }

        public int getNumStatements() {
            return numStatements;
        }
    }
}
