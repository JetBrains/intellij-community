package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.psiutils.ClassUtils;

public class NestedSwitchStatementInspection extends StatementInspection {

    public String getDisplayName() {
        return "Nested 'switch' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Nested '#ref' statement #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new NestedSwitchStatementVisitor(this, inspectionManager, onTheFly);
    }

    private static class NestedSwitchStatementVisitor extends BaseInspectionVisitor {
        private NestedSwitchStatementVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitSwitchStatement(PsiSwitchStatement statement) {
            super.visitSwitchStatement(statement);
            final PsiElement containingSwitchStatement =
                    PsiTreeUtil.getParentOfType(statement, PsiSwitchStatement.class);
            if (containingSwitchStatement == null) {
                return;
            }
            final PsiMethod containingMethod = ClassUtils.getContainingMethod(statement);
            final PsiMethod containingContainingMethod = ClassUtils.getContainingMethod(
                    containingSwitchStatement);
            if(!containingMethod.equals(containingContainingMethod))
            {
                return;
            }
            registerStatementError(statement);
        }
    }

}
