package com.siyeh.ig.errorhandling;

import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
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

    public BaseInspectionVisitor buildVisitor() {
        return new NestedTryStatementVisitor();
    }

    private static class NestedTryStatementVisitor extends StatementInspectionVisitor {

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
