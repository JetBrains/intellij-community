package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class RecursionVisitor extends PsiRecursiveElementVisitor{
    private boolean recursive = false;
    private final PsiMethod method;
    private String methodName;

    public RecursionVisitor(@NotNull PsiMethod method){
        super();
        this.method = method;
        methodName = method.getName();
    }

    public void visitElement(@NotNull PsiElement element){
        if(!recursive){
            super.visitElement(element);
        }
    }

    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call){
        if(recursive)
        {
            return;
        }
        super.visitMethodCallExpression(call);
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        if(methodExpression == null){
            return;
        }
        final String calledMethodName = methodExpression.getReferenceName();
        if(calledMethodName == null)
        {
            return;
        }
        if(!calledMethodName.equals(methodName)){
            return;
        }
        final PsiMethod calledMethod = call.resolveMethod();
        if(method.equals(calledMethod)){
            recursive = true;
        }
    }

    public boolean isRecursive(){
        return recursive;
    }
}
