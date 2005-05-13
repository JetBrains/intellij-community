package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.*;
import org.jetbrains.annotations.NotNull;

public class NestedTryStatementInspection extends StatementInspection {

    public String getDisplayName() {
        return "Nested 'try' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Nested '#ref' statement #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new NestedTryStatementVisitor(this, inspectionManager, onTheFly);
    }

    private static class NestedTryStatementVisitor extends StatementInspectionVisitor {
        private NestedTryStatementVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitTryStatement(@NotNull PsiTryStatement statement) {
            super.visitTryStatement(statement);
            final PsiTryStatement parentTry = PsiTreeUtil.getParentOfType(statement,
                    PsiTryStatement.class);
            if (parentTry == null) {
                return;
            }
            final PsiCodeBlock tryBlock = parentTry.getTryBlock();
            if (!PsiTreeUtil.isAncestor(tryBlock, statement, true)) {
                return;
            }
            registerStatementError(statement);
        }

    }

}
