package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

public class LoopStatementsThatDontLoopInspection extends StatementInspection {
    public String getID(){
        return "LoopStatementThatDoesntLoop";
    }
    public String getDisplayName() {
        return "Loop statement that doesn't loop";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref statement doesn't loop #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new LoopStatementsThatDontLoopVisitor();
    }

    private static class LoopStatementsThatDontLoopVisitor extends StatementInspectionVisitor {


        public void visitForStatement(@NotNull PsiForStatement statement) {
            super.visitForStatement(statement);
            final PsiStatement body = statement.getBody();
            if (body == null) {
                return;
            }
            if (ControlFlowUtils.statementMayCompleteNormally(body)) {
                return;
            }
            if (ControlFlowUtils.statementIsContinueTarget(statement)) {
                return;
            }
            registerStatementError(statement);
        }

        public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
            super.visitForeachStatement(statement);
            final PsiStatement body = statement.getBody();
            if (body == null) {
                return;
            }
            if (ControlFlowUtils.statementMayCompleteNormally(body)) {
                return;
            }
            if (ControlFlowUtils.statementIsContinueTarget(statement)) {
                return;
            }
            registerStatementError(statement);
        }

        public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
            super.visitWhileStatement(statement);
            final PsiStatement body = statement.getBody();
            if (body == null) {
                return;
            }
            if (ControlFlowUtils.statementMayCompleteNormally(body)) {
                return;
            }
            if (ControlFlowUtils.statementIsContinueTarget(statement)) {
                return;
            }
            registerStatementError(statement);
        }

        public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
            super.visitDoWhileStatement(statement);
            final PsiStatement body = statement.getBody();
            if (body == null) {
                return;
            }
            if (ControlFlowUtils.statementMayCompleteNormally(body)) {
                return;
            }
            if (ControlFlowUtils.statementIsContinueTarget(statement)) {
                return;
            }
            registerStatementError(statement);
        }

    }

}
