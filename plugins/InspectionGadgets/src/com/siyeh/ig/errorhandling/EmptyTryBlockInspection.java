package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTryStatement;
import com.siyeh.ig.*;

public class EmptyTryBlockInspection extends StatementInspection {

    public String getDisplayName() {
        return "Empty 'try' block";
    }

    public String getGroupDisplayName() {
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }
    public String buildErrorString(PsiElement location) {
        return "Empty #ref block #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new EmptyTryBlockVisitor(this, inspectionManager, onTheFly);
    }

    private static class EmptyTryBlockVisitor extends StatementInspectionVisitor {
        private EmptyTryBlockVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitTryStatement(PsiTryStatement statement) {
            super.visitTryStatement(statement);
            final PsiCodeBlock finallyBlock = statement.getTryBlock();
            if (finallyBlock == null) {
                return;
            }
            if (finallyBlock.getStatements().length != 0) {
                return;
            }
            registerStatementError(statement);
        }
    }

}
