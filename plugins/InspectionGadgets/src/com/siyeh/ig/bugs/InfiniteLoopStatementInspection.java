package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiDoWhileStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiWhileStatement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.psiutils.ControlFlowUtils;

public class InfiniteLoopStatementInspection extends StatementInspection {

    public String getDisplayName() {
        return "Infinite loop statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref statement cannot complete without throwing an exception #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new InfiniteLoopStatementsVisitor(this, inspectionManager, onTheFly);
    }

    private static class InfiniteLoopStatementsVisitor extends BaseInspectionVisitor {
        private InfiniteLoopStatementsVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitForStatement(PsiForStatement statement) {
            super.visitForStatement(statement);
            if (ControlFlowUtils.statementMayCompleteNormally(statement)) {
                return;
            }
            if (ControlFlowUtils.statementContainsReturn(statement)) {
                return;
            }
            registerStatementError(statement);
        }

        public void visitWhileStatement(PsiWhileStatement statement) {

            super.visitWhileStatement(statement);
            if (ControlFlowUtils.statementMayCompleteNormally(statement)) {
                return;
            }
            if (ControlFlowUtils.statementContainsReturn(statement)) {
                return;
            }
            registerStatementError(statement);
        }

        public void visitDoWhileStatement(PsiDoWhileStatement statement) {
            super.visitDoWhileStatement(statement);
            if (ControlFlowUtils.statementMayCompleteNormally(statement)) {
                return;
            }
            if (ControlFlowUtils.statementContainsReturn(statement)) {
                return;
            }
            registerStatementError(statement);
        }

    }

}
