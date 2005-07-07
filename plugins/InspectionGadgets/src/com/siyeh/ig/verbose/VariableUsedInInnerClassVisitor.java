package com.siyeh.ig.verbose;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

 class VariableUsedInInnerClassVisitor extends PsiRecursiveElementVisitor
{
    private final PsiVariable variable;
    private boolean usedInInnerClass = false;
    private boolean inInnerClass = false;

    VariableUsedInInnerClassVisitor(PsiVariable variable)
    {
        super();
        this.variable = variable;
    }

    public void visitElement(@NotNull PsiElement element){
        if(!usedInInnerClass){
            super.visitElement(element);
        }
    }

    public void visitAnonymousClass(@NotNull PsiAnonymousClass psiAnonymousClass)
    {
        if(usedInInnerClass){
            return;
        }
        final boolean wasInInnerClass = inInnerClass;
        inInnerClass = true;
        super.visitAnonymousClass(psiAnonymousClass);
        inInnerClass = wasInInnerClass;
    }

    public void visitReferenceExpression(@NotNull PsiReferenceExpression reference)
    {
        if(usedInInnerClass){
            return;
        }
        super.visitReferenceExpression(reference);
        if(inInnerClass){
            final PsiElement element = reference.resolve();
            if(variable.equals(element)){
                usedInInnerClass = true;
            }
        }
    }

    public boolean isUsedInInnerClass()
    {
        return usedInInnerClass;
    }
}
