package com.siyeh.ig.controlflow;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class BreakStatementInspection extends StatementInspection {

    public String getDisplayName() {
        return "'break' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref statement #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new BreakStatementVisitor();
    }

    private static class BreakStatementVisitor extends StatementInspectionVisitor {

        public void visitBreakStatement(@NotNull PsiBreakStatement statement) {
            super.visitBreakStatement(statement);

            final PsiSwitchStatement switchStatement =
                    PsiTreeUtil.getParentOfType(statement, PsiSwitchStatement.class);
            if (switchStatement != null &&
                    isBreakAtTopLevel(switchStatement, statement)) {
                return;
            }
            registerStatementError(statement);
        }

        private static boolean isBreakAtTopLevel(PsiSwitchStatement switchStatement, PsiBreakStatement statement) {
            final PsiCodeBlock body = switchStatement.getBody();
            if (body == null) {
                return false;
            }
            final PsiStatement[] statements = body.getStatements();
            for(final PsiStatement child : statements){
                if(child.equals(statement)){
                    return true;
                }
            }
            return false;
        }

    }

}
