package com.siyeh.ig.controlflow;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class NestedSwitchStatementInspection extends StatementInspection {

    public String getDisplayName() {
        return "Nested 'switch' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Nested '#ref' statement #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NestedSwitchStatementVisitor();
    }

    private static class NestedSwitchStatementVisitor extends StatementInspectionVisitor {

        public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
            super.visitSwitchStatement(statement);
            final PsiElement containingSwitchStatement =
                    PsiTreeUtil.getParentOfType(statement, PsiSwitchStatement.class);
            if (containingSwitchStatement == null) {
                return;
            }
            final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(statement,
                                                                 PsiMethod.class);
            final PsiMethod containingContainingMethod = PsiTreeUtil.getParentOfType(containingSwitchStatement,
                                                                 PsiMethod.class);
            if(containingMethod == null || containingContainingMethod == null||
                    containingMethod.equals(containingContainingMethod))
            {
                return;
            }
            registerStatementError(statement);
        }
    }

}
