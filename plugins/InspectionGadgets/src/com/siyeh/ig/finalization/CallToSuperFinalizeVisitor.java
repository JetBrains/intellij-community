package com.siyeh.ig.finalization;

import com.intellij.psi.*;

class CallToSuperFinalizeVisitor extends PsiRecursiveElementVisitor{
    private boolean callToSuperFinalizeFound = false;

    public void visitElement(PsiElement element){
        if(!callToSuperFinalizeFound){
            super.visitElement(element);
        }
    }

    public void visitMethodCallExpression(PsiMethodCallExpression expression){
        if(callToSuperFinalizeFound){
            return;
        }
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
        callToSuperFinalizeFound = true;
    }

    public boolean isCallToSuperFinalizeFound(){
        return callToSuperFinalizeFound;
    }
}
