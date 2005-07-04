package com.siyeh.ig.threading;

import com.intellij.psi.*;

class ContainsSynchronizationVisitor extends PsiRecursiveElementVisitor{
    private boolean containsSynchronization = false;

    public void visitElement(PsiElement element){
        if(containsSynchronization)
        {
            return;
        }
        super.visitElement(element);
    }

    public void visitSynchronizedStatement(PsiSynchronizedStatement statement){
        containsSynchronization = true;
    }

    public void visitMethod(PsiMethod method){
        if(method.hasModifierProperty(PsiModifier.SYNCHRONIZED))
        {
            containsSynchronization = true;
            return;
        }
        super.visitMethod(method);
    }

    public boolean containsSynchronization(){
        return containsSynchronization;
    }
}
