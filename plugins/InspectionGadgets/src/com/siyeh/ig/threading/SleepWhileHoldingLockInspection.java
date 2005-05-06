package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ClassUtils;

public class SleepWhileHoldingLockInspection extends ExpressionInspection{
    public String getDisplayName(){
        return "Call to Thread.sleep() while synchronized";
    }

    public String getGroupDisplayName(){
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Call to Thread.#ref() while synchronized #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new sleepWhileHoldingLock(this, inspectionManager, onTheFly);
    }

    private static class sleepWhileHoldingLock extends BaseInspectionVisitor{
        private sleepWhileHoldingLock(BaseInspection inspection,
                                      InspectionManager inspectionManager,
                                      boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if(methodExpression == null){
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if(!"sleep".equals(methodName)){
                return;
            }
            final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expression,
                                                                           PsiMethod.class);
            boolean isSynced = false;
            if(containingMethod != null && containingMethod
                    .hasModifierProperty(PsiModifier.SYNCHRONIZED)){
                isSynced = true;
            }
            final PsiSynchronizedStatement containingSyncStatement =
                    PsiTreeUtil.getParentOfType(expression,
                                                PsiSynchronizedStatement.class);
            if(containingSyncStatement != null){
                isSynced = true;
            }
            if(!isSynced){
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if(method == null){
                return;
            }
            final PsiClass methodClass = method.getContainingClass();
            if(methodClass == null ||
                    !ClassUtils.isSubclass(methodClass, "java.lang.Thread")){
                return;
            }
            registerMethodCallError(expression);
        }
    }
}
