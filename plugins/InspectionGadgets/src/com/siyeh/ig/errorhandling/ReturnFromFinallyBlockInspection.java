package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReturnStatement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.psiutils.ControlFlowUtils;

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

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new ReturnFromFinallyBlockVisitor(this, inspectionManager,
                                                 onTheFly);
    }

    private static class ReturnFromFinallyBlockVisitor
            extends BaseInspectionVisitor{
        private ReturnFromFinallyBlockVisitor(BaseInspection inspection,
                                              InspectionManager inspectionManager,
                                              boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitReturnStatement(PsiReturnStatement statement){
            super.visitReturnStatement(statement);
            if(!ControlFlowUtils.isInFinallyBlock(statement)){
                return;
            }
            registerStatementError(statement);
        }
    }
}
