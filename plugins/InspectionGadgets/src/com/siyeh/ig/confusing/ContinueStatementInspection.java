package com.siyeh.ig.confusing;

import com.intellij.psi.PsiContinueStatement;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class ContinueStatementInspection extends StatementInspection {

    public String getDisplayName() {
        return "'continue' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref statement #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ContinueStatementVisitor();
    }

    private static class ContinueStatementVisitor extends StatementInspectionVisitor {

        public void visitContinueStatement(@NotNull PsiContinueStatement statement) {
            super.visitContinueStatement(statement);
            registerStatementError(statement);
        }

    }

}
