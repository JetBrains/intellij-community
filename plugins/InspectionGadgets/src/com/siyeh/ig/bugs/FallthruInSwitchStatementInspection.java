package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

public class FallthruInSwitchStatementInspection extends StatementInspection {
    public String getID(){
        return "FallthroughInSwitchStatement";
    }
    public String getDisplayName() {
        return "Fallthrough in 'switch' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref fallthrough in 'switch' statement #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new FallthroughInSwitchStatementVisitor(this, inspectionManager, onTheFly);
    }

    private static class FallthroughInSwitchStatementVisitor extends StatementInspectionVisitor {
        private FallthroughInSwitchStatementVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
            super.visitSwitchStatement(statement);
            boolean switchLabelValid = true;
            final PsiCodeBlock body = statement.getBody();
            if (body == null) {
                return;
            }
            final PsiStatement[] statements = body.getStatements();
            for(final PsiStatement child : statements){
                if(child instanceof PsiSwitchLabelStatement){
                    if(!switchLabelValid){
                        registerError(child);
                    }
                    switchLabelValid = true;
                } else{
                    switchLabelValid = !ControlFlowUtils.statementMayCompleteNormally(child);
                }
            }
        }
    }

}