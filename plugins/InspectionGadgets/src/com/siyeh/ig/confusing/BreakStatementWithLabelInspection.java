package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiBreakStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.siyeh.ig.*;

public class BreakStatementWithLabelInspection extends StatementInspection {

    public String getDisplayName() {
        return "'break' statement with label";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref statement with label #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new BreakStatementWithLabelVisitor(this, inspectionManager, onTheFly);
    }

    private static class BreakStatementWithLabelVisitor extends StatementInspectionVisitor {
        private BreakStatementWithLabelVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitBreakStatement(PsiBreakStatement statement) {
            super.visitBreakStatement(statement);
            final PsiIdentifier labelIdentifier = statement.getLabelIdentifier();
            if (labelIdentifier == null) {
                return;
            }
            final PsiIdentifier identifier = statement.getLabelIdentifier();
            final String labelText = identifier.getText();
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
