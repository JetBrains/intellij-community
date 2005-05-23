package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSwitchStatement;
import com.siyeh.ig.*;
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