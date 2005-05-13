package com.siyeh.ig.finalization;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

class CallToSuperFinalizeVisitor extends PsiRecursiveElementVisitor{
    private boolean callToSuperFinalizeFound = false;

    public void visitElement(@NotNull PsiElement element){
        if(!callToSuperFinalizeFound){
            super.visitElement(element);
        }
    }

    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression){
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
