package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiThrowStatement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.psiutils.ControlFlowUtils;

public class ThrowFromFinallyBlockInspection extends StatementInspection {

    public String getDisplayName() {
        return "'throw' inside 'finally' block";
    }

    public String getGroupDisplayName() {
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "'#ref' inside 'finally' block #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ThrowFromFinallyBlockVisitor(this, inspectionManager, onTheFly);
    }

    private static class ThrowFromFinallyBlockVisitor extends BaseInspectionVisitor {
        private ThrowFromFinallyBlockVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitThrowStatement(PsiThrowStatement statement) {
            super.visitThrowStatement(statement);
            if (!ControlFlowUtils.isInFinallyBlock(statement)) {
                return;
            }
            registerStatementError(statement);
        }
    }

}
