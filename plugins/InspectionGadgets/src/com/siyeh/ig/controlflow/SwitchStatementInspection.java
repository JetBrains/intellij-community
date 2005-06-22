package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSwitchStatement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class SwitchStatementInspection extends StatementInspection {

    public String getDisplayName() {
        return "'switch' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "'#ref' statement #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SwitchStatementVisitor();
    }

    private static class SwitchStatementVisitor extends StatementInspectionVisitor {

        public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
            super.visitSwitchStatement(statement);
            registerStatementError(statement);
        }

    }

}