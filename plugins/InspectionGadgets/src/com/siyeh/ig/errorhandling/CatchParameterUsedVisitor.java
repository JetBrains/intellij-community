package com.siyeh.ig.errorhandling;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.NotNull;

class CatchParameterUsedVisitor extends PsiRecursiveElementVisitor{
    private final PsiParameter parameter;
    private boolean used = false;

    CatchParameterUsedVisitor(PsiParameter variable){
        super();
        parameter = variable;
    }

    public void visitElement(@NotNull PsiElement element){
        if(!used){
            super.visitElement(element);
        }
    }

    public void visitReferenceExpression(@NotNull PsiReferenceExpression reference){
        if(used){
            return;
        }
        super.visitReferenceExpression(reference);
        final PsiElement element = reference.resolve();
        if(parameter.equals(element)){
            used = true;
        }
    }

    public boolean isUsed(){
        return used;
    }
}
