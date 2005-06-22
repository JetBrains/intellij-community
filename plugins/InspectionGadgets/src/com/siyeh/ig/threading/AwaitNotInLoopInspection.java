package com.siyeh.ig.threading;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class AwaitNotInLoopInspection extends ExpressionInspection {

    public String getDisplayName() {
        return "'await()' not in loop";
    }

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Call to '#ref()' is not made in a loop #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new AwaitNotInLoopVisitor();
    }

    private static class AwaitNotInLoopVisitor extends BaseInspectionVisitor {

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if(methodName == null)
            {
                return;
            }
            if (!methodName.startsWith("await")) {
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if(containingClass == null){
                return;
            }
            if(!ClassUtils.isSubclass(containingClass,
                                "java.util.concurrent.locks.Condition")){
                return;
            }

            if (ControlFlowUtils.isInLoop(expression)) {
                return;
            }
            registerMethodCallError(expression);
        }
    }

}
