package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

public class TailRecursionInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "Tail recursion";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Tail recursive call #ref() #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new TailRecursionVisitor(this, inspectionManager, onTheFly);
    }

    private static class TailRecursionVisitor extends BaseInspectionVisitor {
        private TailRecursionVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitReturnStatement(PsiReturnStatement statement) {
            super.visitReturnStatement(statement);
            final PsiExpression returnValue = statement.getReturnValue();
            if (returnValue == null) {
                return;
            }
            if (!(returnValue instanceof PsiMethodCallExpression)) {
                return;
            }
            final PsiMethod containingMethod =
                    (PsiMethod) PsiTreeUtil.getParentOfType(statement, PsiMethod.class);
            if (containingMethod == null) {
                return;
            }
            final PsiMethodCallExpression returnCall = (PsiMethodCallExpression) returnValue;
            final PsiMethod method = returnCall.resolveMethod();
            if (method == null) {
                return;
            }
            if (!method.equals(containingMethod)) {
                return;
            }

            final PsiReferenceExpression methodExpression = returnCall.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            registerMethodCallError(returnCall);
        }
    }

}
