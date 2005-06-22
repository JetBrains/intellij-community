package com.siyeh.ig.threading;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSynchronizedStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import org.jetbrains.annotations.NotNull;

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

    public BaseInspectionVisitor buildVisitor() {
        return new NestedSynchronizedStatementVisitor();
    }

    private static class NestedSynchronizedStatementVisitor extends StatementInspectionVisitor {
       
        public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement statement) {
            super.visitSynchronizedStatement(statement);
            final PsiElement containingSynchronizedStatement =
                    PsiTreeUtil.getParentOfType(statement, PsiSynchronizedStatement.class);
            if(containingSynchronizedStatement == null){
                return;
            }
            final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(statement,
                                                                 PsiMethod.class);
            final PsiMethod containingContainingMethod = PsiTreeUtil.getParentOfType(containingSynchronizedStatement,
                                                                 PsiMethod.class);
            if(containingMethod == null || containingContainingMethod == null ||
                    !containingMethod.equals(containingContainingMethod)){
                return;
            }
            registerStatementError(statement);
        }

    }

}
