package com.siyeh.ig.errorhandling;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.jsp.JspFile;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
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

    public BaseInspectionVisitor buildVisitor() {
        return new EmptyFinallyBlockVisitor();
    }

    private static class EmptyFinallyBlockVisitor extends StatementInspectionVisitor {


        public void visitTryStatement(@NotNull PsiTryStatement statement) {
            super.visitTryStatement(statement);

            if(statement.getContainingFile() instanceof JspFile){
                return;
            }
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
