package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiContinueStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.siyeh.ig.*;

public class ContinueStatementWithLabelInspection extends StatementInspection {

    public String getDisplayName() {
        return "'continue' statement with label";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref statement with label #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ContinueStatementVisitor(this, inspectionManager, onTheFly);
    }

    private static class ContinueStatementVisitor extends StatementInspectionVisitor {
        private ContinueStatementVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitContinueStatement(PsiContinueStatement statement) {
            super.visitContinueStatement(statement);
            final PsiIdentifier label = statement.getLabelIdentifier();
            if (label == null) {
                return;
            }
            final String labelText = label.getText();
            if (labelText == null) {
                return;
            }
            if (labelText.length() == 0) {
                return;
            }
            registerStatementError(statement);
        }

    }

}
