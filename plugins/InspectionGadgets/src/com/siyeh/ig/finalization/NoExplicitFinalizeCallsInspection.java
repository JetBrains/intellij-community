package com.siyeh.ig.finalization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

public class NoExplicitFinalizeCallsInspection extends ExpressionInspection {
    public String getID(){
        return "FinalizeCalledExplicitly";
    }
    public String getDisplayName() {
        return "'finalize()' called explicitly";
    }

    public String getGroupDisplayName() {
        return GroupNames.FINALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref() called explicitly #loc";
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new NoExplicitFinalizeCallsVisitor(this, inspectionManager, onTheFly);
    }

    private static class NoExplicitFinalizeCallsVisitor extends BaseInspectionVisitor {
        private NoExplicitFinalizeCallsVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if (!"finalize".equals(methodName)) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            if (argumentList.getExpressions().length != 0) {
                return;
            }
            final PsiMethod containingMethod =
                    (PsiMethod) PsiTreeUtil.getParentOfType(expression,
                                                            PsiMethod.class);
            final String containingMethodName = containingMethod.getName();
            final PsiParameterList parameterList = containingMethod.getParameterList();
            if ("finalize".equals(containingMethodName)
                    && parameterList.getParameters().length == 0) {
                return;
            }
            registerMethodCallError(expression);
        }
    }

}
