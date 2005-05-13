package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class VariableUsedInInnerClassVisitor extends PsiRecursiveElementVisitor{
    private final @NotNull PsiVariable variable;
    private boolean usedInInnerClass = false;
    private boolean inInnerClass = false;

    public VariableUsedInInnerClassVisitor(@NotNull PsiVariable variable){
        super();
        this.variable = variable;
    }

    public void visitElement(@NotNull PsiElement element){
        if(!usedInInnerClass){
            super.visitElement(element);
        }
    }

    public void visitAnonymousClass(@NotNull PsiAnonymousClass psiAnonymousClass){
        if(usedInInnerClass){
            return;
        }
        final boolean wasInInnerClass = inInnerClass;
        inInnerClass = true;
        super.visitAnonymousClass(psiAnonymousClass);
        inInnerClass = wasInInnerClass;
    }

    public void visitReferenceExpression(@NotNull PsiReferenceExpression ref){
        if(usedInInnerClass){
            return;
        }
        super.visitReferenceExpression(ref);

        if(!inInnerClass){
            return;
        }
        final PsiElement element = ref.resolve();
        if(variable.equals(element)){
            usedInInnerClass = true;
        }
    }

    public boolean isUsedInInnerClass(){
        return usedInInnerClass;
    }
}
