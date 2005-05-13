package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSynchronizedStatement;
import com.siyeh.ig.*;
import org.jetbrains.annotations.NotNull;

public class EmptySynchronizedStatementInspection extends StatementInspection {
    public String getDisplayName() {
        return "Empty 'synchronized' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Empty #ref statement #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new EmptySynchronizedStatementVisitor(this, inspectionManager, onTheFly);
    }

    private static class EmptySynchronizedStatementVisitor extends StatementInspectionVisitor {
        private EmptySynchronizedStatementVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement statement) {
            super.visitSynchronizedStatement(statement);
            final PsiCodeBlock body = statement.getBody();
            if (body == null) {
                return;
            }
            final PsiStatement[] statements = body.getStatements();
            if (statements == null) {
                return;
            }
            if (statements.length > 0) {
                return;
            }
            registerStatementError(statement);
        }

    }

}
