package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiContinueStatement;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.*;
import org.jetbrains.annotations.NotNull;

public class ContinueStatementInspection extends StatementInspection {

    public String getDisplayName() {
        return "'continue' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref statement #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ContinueStatementVisitor(this, inspectionManager, onTheFly);
    }

    private static class ContinueStatementVisitor extends StatementInspectionVisitor {
        private ContinueStatementVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitContinueStatement(@NotNull PsiContinueStatement statement) {
            super.visitContinueStatement(statement);
            registerStatementError(statement);
        }

    }

}
