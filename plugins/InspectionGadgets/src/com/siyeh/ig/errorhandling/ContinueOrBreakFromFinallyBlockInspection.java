package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiBreakStatement;
import com.intellij.psi.PsiContinueStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiStatement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.psiutils.ControlFlowUtils;

public class ContinueOrBreakFromFinallyBlockInspection extends StatementInspection {

    public String getDisplayName() {
        return "'continue' or 'break' inside 'finally' block";
    }

    public String getGroupDisplayName() {
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "'#ref' inside 'finally' block #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ContinueOrBreakFromFinallyBlockVisitor(this, inspectionManager, onTheFly);
    }

    private static class ContinueOrBreakFromFinallyBlockVisitor extends BaseInspectionVisitor {
        private ContinueOrBreakFromFinallyBlockVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitContinueStatement(PsiContinueStatement statement) {
            super.visitContinueStatement(statement);
            if (!ControlFlowUtils.isInFinallyBlock(statement)) {
                return;
            }
            final PsiStatement continuedStatement = statement.findContinuedStatement();
            if (ControlFlowUtils.isInFinallyBlock(continuedStatement)) {
                return;
            }
            registerStatementError(statement);
        }

        public void visitBreakStatement(PsiBreakStatement statement) {
            super.visitBreakStatement(statement);
            if (!ControlFlowUtils.isInFinallyBlock(statement)) {
                return;
            }
            final PsiStatement exitedStatement = statement.findExitedStatement();
            if (ControlFlowUtils.isInFinallyBlock(exitedStatement)) {
                return;
            }
            registerStatementError(statement);
        }
    }

}
