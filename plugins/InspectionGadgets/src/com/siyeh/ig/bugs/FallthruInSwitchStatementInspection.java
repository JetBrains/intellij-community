package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.psiutils.ControlFlowUtils;

public class FallthruInSwitchStatementInspection extends StatementInspection {

    public String getDisplayName() {
        return "Fallthrough in 'switch' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref fallthrough in 'switch' statement #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new FallthroughInSwitchStatementVisitor(this, inspectionManager, onTheFly);
    }

    private static class FallthroughInSwitchStatementVisitor extends BaseInspectionVisitor {
        private FallthroughInSwitchStatementVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitSwitchStatement(PsiSwitchStatement statement) {
            super.visitSwitchStatement(statement);
            boolean switchLabelValid = true;
            final PsiCodeBlock body = statement.getBody();
            if (body == null) {
                return;
            }
            final PsiStatement[] statements = body.getStatements();
            for (int i = 0; i < statements.length; i++) {
                final PsiStatement child = statements[i];
                if (child instanceof PsiSwitchLabelStatement) {
                    if (!switchLabelValid) {
                        registerError(child);
                    }
                    switchLabelValid = true;
                } else {
                    switchLabelValid = !ControlFlowUtils.statementMayCompleteNormally(child);
                }
            }
        }
    }

}