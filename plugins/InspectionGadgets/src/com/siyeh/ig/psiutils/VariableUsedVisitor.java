package com.siyeh.ig.psiutils;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;

public class VariableUsedVisitor extends PsiRecursiveElementVisitor{
    private boolean used = false;
    private final PsiVariable variable;

    public VariableUsedVisitor(PsiVariable variable){
        super();
        this.variable = variable;
    }

    public void visitElement(PsiElement element){
        if(!used){
            super.visitElement(element);
        }
    }

    public void visitReferenceExpression(PsiReferenceExpression ref){
        if(used){
            return;
        }
        super.visitReferenceExpression(ref);

        final PsiElement referent = ref.resolve();
        if(referent == null){
            return;
        }
        if(referent.equals(variable)){
            used = true;
        }
    }

    public boolean isUsed(){
        return used;
    }
}
