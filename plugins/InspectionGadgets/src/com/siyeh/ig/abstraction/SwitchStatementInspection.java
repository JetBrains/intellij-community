package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSwitchStatement;
import com.siyeh.ig.*;

public class SwitchStatementInspection extends StatementInspection {

    public String getDisplayName() {
        return "'switch' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "'#ref' statement #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SwitchStatementVisitor(this, inspectionManager, onTheFly);
    }

    private static class SwitchStatementVisitor extends StatementInspectionVisitor {
        private SwitchStatementVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitSwitchStatement(PsiSwitchStatement statement) {
            super.visitSwitchStatement(statement);
            registerStatementError(statement);
        }

    }

}