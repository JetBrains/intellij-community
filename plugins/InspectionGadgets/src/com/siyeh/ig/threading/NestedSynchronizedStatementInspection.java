package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSynchronizedStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;

public class NestedSynchronizedStatementInspection extends StatementInspection {

    public String getDisplayName() {
        return "Nested 'synchronized' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Nested #ref statement #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new NestedSynchronizedStatementVisitor(this, inspectionManager, onTheFly);
    }

    private static class NestedSynchronizedStatementVisitor extends BaseInspectionVisitor {
        private NestedSynchronizedStatementVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitSynchronizedStatement(PsiSynchronizedStatement statement) {
            super.visitSynchronizedStatement(statement);
            if (PsiTreeUtil.getParentOfType(statement, PsiSynchronizedStatement.class) == null) {
                return;
            }
            registerStatementError(statement);
        }

    }

}
