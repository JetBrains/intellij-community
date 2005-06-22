package com.siyeh.ig.errorhandling;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReturnStatement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

public class ReturnFromFinallyBlockInspection extends StatementInspection{
    public String getID(){
        return "ReturnInsideFinallyBlock";
    }

    public String getDisplayName(){
        return "'return' inside 'finally' block";
    }

    public String getGroupDisplayName(){
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        return "'#ref' inside 'finally' block #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ReturnFromFinallyBlockVisitor();
    }

    private static class ReturnFromFinallyBlockVisitor
            extends StatementInspectionVisitor{

        public void visitReturnStatement(@NotNull PsiReturnStatement statement){
            super.visitReturnStatement(statement);
            if(!ControlFlowUtils.isInFinallyBlock(statement)){
                return;
            }
            registerStatementError(statement);
        }
    }
}
