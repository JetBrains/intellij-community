package com.siyeh.ig.jdk;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiForeachStatement;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;

public class ForeachStatementInspection extends StatementInspection {

    public String getDisplayName() {
        return "Extended 'for' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.JDK_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "'Extended #ref' statement #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ForeachStatementVisitor(this, inspectionManager, onTheFly);
    }

    private static class ForeachStatementVisitor extends BaseInspectionVisitor {
        private ForeachStatementVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitForeachStatement(PsiForeachStatement statement) {
            super.visitForeachStatement(statement);
            registerStatementError(statement);
        }
    }
}