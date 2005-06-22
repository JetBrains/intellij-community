package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

public class FallthruInSwitchStatementInspection extends StatementInspection {
    public String getID(){
        return "fallthrough";
    }
    public String getDisplayName() {
        return "Fallthrough in 'switch' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref fallthrough in 'switch' statement #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new FallthroughInSwitchStatementVisitor();
    }

    private static class FallthroughInSwitchStatementVisitor extends StatementInspectionVisitor {

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