package com.siyeh.ig.cloneable;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

class CallToSuperCloneVisitor extends PsiRecursiveElementVisitor{
    private boolean callToSuperCloneFound = false;

    public void visitElement(@NotNull PsiElement element){
        if(!callToSuperCloneFound){
            super.visitElement(element);
        }
    }

    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression){
        if(callToSuperCloneFound){
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
        if(!"clone".equals(methodName)){
            return;
        }
        callToSuperCloneFound = true;
    }

    public boolean isCallToSuperCloneFound(){
        return callToSuperCloneFound;
    }
}
