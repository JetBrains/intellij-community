package com.siyeh.ig.finalization;

import com.intellij.psi.*;

class CallToSuperFinalizeVisitor extends PsiRecursiveElementVisitor {
    private boolean m_callToSuperFinalizeFound = false;

    public void visitMethodCallExpression(PsiMethodCallExpression expression){
        super.visitMethodCallExpression(expression);
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        if(methodExpression == null){
            return;
        }
        final PsiExpression target = methodExpression.getQualifierExpression();
        if(!(target instanceof PsiSuperExpression)){
            return;
        }
        final String methodName = methodExpression.getReferenceName();
        if(!"finalize".equals(methodName)){
            return;
        }
        m_callToSuperFinalizeFound = true;
    }


    public boolean isCallToSuperFinalizeFound() {
        return m_callToSuperFinalizeFound;
    }
}
