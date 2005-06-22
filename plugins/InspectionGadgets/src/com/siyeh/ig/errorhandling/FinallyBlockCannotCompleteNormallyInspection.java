package com.siyeh.ig.errorhandling;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTryStatement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

public class FinallyBlockCannotCompleteNormallyInspection extends StatementInspection {
    public String getID(){
        return "finally";
    }

    public String getDisplayName() {
        return "'finally' block which can not complete normally";
    }

    public String getGroupDisplayName() {
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }
    public String buildErrorString(PsiElement location) {
        return "#ref block can not complete normally #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new FinallyBlockCannotCompleteNormallyVisitor();
    }

    private static class FinallyBlockCannotCompleteNormallyVisitor extends StatementInspectionVisitor {


        public void visitTryStatement(@NotNull PsiTryStatement statement) {
            super.visitTryStatement(statement);
            final PsiCodeBlock finallyBlock = statement.getFinallyBlock();
            if (finallyBlock == null) {
                return;
            }
            if(ControlFlowUtils.codeBlockMayCompleteNormally(finallyBlock)){
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
