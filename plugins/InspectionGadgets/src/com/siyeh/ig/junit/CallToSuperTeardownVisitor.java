package com.siyeh.ig.junit;

import com.intellij.psi.*;

class CallToSuperTeardownVisitor extends PsiRecursiveElementVisitor{
    private boolean callToSuperTearDownFound = false;

    public void visitElement(PsiElement element){
        if(!callToSuperTearDownFound){
            super.visitElement(element);
        }
    }

    public void visitMethodCallExpression(PsiMethodCallExpression expression){
        if(callToSuperTearDownFound){
            return;
        }
        super.visitMethodCallExpression(expression);
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        if(methodExpression == null){
            return;
        }
        final String methodName = methodExpression.getReferenceName();
        if(!"tearDown".equals(methodName)){
            return;
        }
        final PsiExpression target = methodExpression.getQualifierExpression();
        if(!(target instanceof PsiSuperExpression)){
            return;
        }

        callToSuperTearDownFound = true;
    }

    public boolean isCallToSuperTeardownFound(){
        return callToSuperTearDownFound;
    }
}
