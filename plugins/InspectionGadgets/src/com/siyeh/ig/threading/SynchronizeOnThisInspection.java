package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;
import org.jetbrains.annotations.NotNull;

public class SynchronizeOnThisInspection extends MethodInspection {

    public String getDisplayName() {
        return "Synchronization on 'this'";
    }

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Lock operations on 'this' may have unforseen side-effects #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SynchronizeOnThisVisitor(this, inspectionManager, onTheFly);
    }

    private static class SynchronizeOnThisVisitor extends BaseInspectionVisitor {
        private SynchronizeOnThisVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement statement){
            super.visitSynchronizedStatement(statement);
            final PsiExpression lockExpression = statement.getLockExpression();
            if(!(lockExpression instanceof PsiThisExpression)){
                return;
            }
            registerError(lockExpression);
        }

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            if(methodExpression == null){
                return;
            }
            final PsiExpression qualifier = methodExpression.getQualifierExpression();
            if(qualifier != null && !(qualifier instanceof PsiThisExpression))
            {
                return;
            }
            if(!isNotify(expression) && !isWait(expression))
            {
                return;
            }
            registerMethodCallError(expression);
        }

        private static boolean isWait(PsiMethodCallExpression expression){
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();

            if(!"wait".equals(methodName)){
                return false;
            }
            final PsiMethod method = expression.resolveMethod();
            if(method == null){
                return false;
            }
            final PsiParameterList paramList = method.getParameterList();
            if(paramList == null){
                return false;
            }
            final PsiParameter[] parameters = paramList.getParameters();
            final int numParams = parameters.length;
            if(numParams > 2){
                return false;
            }
            if(numParams > 0){
                final PsiType parameterType = parameters[0].getType();
                if(!parameterType.equals(PsiType.LONG)){
                    return false;
                }
            }

            if(numParams > 1){
                final PsiType parameterType = parameters[1].getType();
                if(!parameterType.equals(PsiType.INT)){
                    return false;
                }
            }
            return true;
        }

        private static boolean isNotify(PsiMethodCallExpression expression){
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if(!"notify".equals(methodName) && !"notifyAll".equals(methodName)){
                return false;
            }
            final PsiMethod method = expression.resolveMethod();
            if(method == null){
                return false;
            }
            final PsiParameterList paramList = method.getParameterList();
            if(paramList == null){
                return false;
            }
            final PsiParameter[] parameters = paramList.getParameters();
            final int numParams = parameters.length;
            return numParams == 0;
        }
    }



}
