package com.siyeh.ig.errorhandling;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiThrowStatement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;

public class ThrowFromFinallyBlockInspection extends StatementInspection {

    public String getDisplayName() {
        return "'throw' inside 'finally' block";
    }

    public String getGroupDisplayName() {
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }
    public String buildErrorString(PsiElement location) {
        return "'#ref' inside 'finally' block #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ThrowFromFinallyBlockVisitor();
    }

    private static class ThrowFromFinallyBlockVisitor extends StatementInspectionVisitor {
       
        public void visitThrowStatement(PsiThrowStatement statement) {
            super.visitThrowStatement(statement);
            if (!ControlFlowUtils.isInFinallyBlock(statement)) {
                return;
            }
            registerStatementError(statement);
        }
    }

}
