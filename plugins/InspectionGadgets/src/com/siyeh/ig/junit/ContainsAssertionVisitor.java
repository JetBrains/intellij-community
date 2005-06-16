package com.siyeh.ig.junit;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.NotNull;

class ContainsAssertionVisitor extends PsiRecursiveElementVisitor{
    private boolean containsAssertion = false;

    public void visitElement(@NotNull PsiElement element){
        if(!containsAssertion){
            super.visitElement(element);
        }
    }

    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call){
        if(containsAssertion){
            return;
        }
        super.visitMethodCallExpression(call);
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        if(methodExpression == null){
            return;
        }
        final String methodName = methodExpression.getReferenceName();
        if(methodName == null)
        {
            return;
        }
        if(methodName.startsWith("assert") || methodName.startsWith("fail")){
            containsAssertion = true;
        }
    }

    public boolean containsAssertion(){
        return containsAssertion;
    }
}
