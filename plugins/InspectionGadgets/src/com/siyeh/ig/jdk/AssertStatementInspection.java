package com.siyeh.ig.jdk;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiAssertStatement;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.*;

public class AssertStatementInspection extends StatementInspection {

    public String getDisplayName() {
        return "'assert' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.JDK_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "'#ref' statement #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new AssertStatementVisitor(this, inspectionManager, onTheFly);
    }

    private static class AssertStatementVisitor extends StatementInspectionVisitor {
        private AssertStatementVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitAssertStatement(PsiAssertStatement statement) {
            super.visitAssertStatement(statement);
            registerStatementError(statement);
        }

    }

}