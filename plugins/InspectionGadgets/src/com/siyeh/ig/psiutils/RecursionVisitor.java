package com.siyeh.ig.psiutils;

import com.intellij.psi.*;

public class RecursionVisitor extends PsiRecursiveElementVisitor{
    private boolean recursive = false;
    private final PsiMethod method;
    private String methodName;

    public RecursionVisitor(PsiMethod method){
        super();
        this.method = method;
        methodName = method.getName();
    }

    public void visitElement(PsiElement element){
        if(!recursive){
            super.visitElement(element);
        }
    }

    public void visitMethodCallExpression(PsiMethodCallExpression call){
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
        if(calledMethodName.equals(methodName)){
            final PsiMethod calledMethod = call.resolveMethod();
            if(calledMethod != null){
                if(calledMethod.equals(method)){
                    recursive = true;
                }
            }
        }
    }

    public boolean isRecursive(){
        return recursive;
    }
}
