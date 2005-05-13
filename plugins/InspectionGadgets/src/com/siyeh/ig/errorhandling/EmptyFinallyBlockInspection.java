package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTryStatement;
import com.siyeh.ig.*;
import org.jetbrains.annotations.NotNull;

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

    private static class EmptyFinallyBlockVisitor extends StatementInspectionVisitor {
        private EmptyFinallyBlockVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitTryStatement(@NotNull PsiTryStatement statement) {
            super.visitTryStatement(statement);
            final PsiCodeBlock finallyBlock = statement.getFinallyBlock();
            if (finallyBlock == null) {
                return;
            }
            if (finallyBlock.getStatements().length != 0) {
                return;
            }
            final PsiElement[] children = statement.getChildren();
            for(final PsiElement child : children){
                final String childText = child.getText();
                if("finally".equals(childText)){
                    registerError(child);
                    return;
                }
            }
        }
    }

}
