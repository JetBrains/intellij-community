package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTryStatement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;

public class EmptyFinallyBlockInspection extends StatementInspection {

    public String getDisplayName() {
        return "Empty 'finally' block";
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
        return new EmptyFinallyBlockVisitor(this, inspectionManager, onTheFly);
    }

    private static class EmptyFinallyBlockVisitor extends BaseInspectionVisitor {
        private EmptyFinallyBlockVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitTryStatement(PsiTryStatement statement) {
            super.visitTryStatement(statement);
            final PsiCodeBlock finallyBlock = statement.getFinallyBlock();
            if (finallyBlock == null) {
                return;
            }
            if (finallyBlock.getStatements().length != 0) {
                return;
            }
            final PsiElement[] children = statement.getChildren();
            for (int j = 0; j < children.length; j++) {
                final PsiElement child = children[j];
                final String childText = child.getText();
                if ("finally".equals(childText)) {
                    registerError(child);
                    return;
                }
            }
        }
    }

}
