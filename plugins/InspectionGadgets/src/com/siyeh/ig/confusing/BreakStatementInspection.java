package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;

public class BreakStatementInspection extends StatementInspection {

    public String getDisplayName() {
        return "'break' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref statement #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new BreakStatementVisitor(this, inspectionManager, onTheFly);
    }

    private static class BreakStatementVisitor extends BaseInspectionVisitor {
        private BreakStatementVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitBreakStatement(PsiBreakStatement statement) {
            super.visitBreakStatement(statement);

            final PsiSwitchStatement switchStatement =
                    (PsiSwitchStatement) PsiTreeUtil.getParentOfType(statement, PsiSwitchStatement.class);
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
            for (int i = 0; i < statements.length; i++) {
                final PsiStatement child = statements[i];
                if (child.equals(statement)) {
                    return true;
                }
            }
            return false;
        }

    }

}
