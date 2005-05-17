package com.siyeh.ipp.decls;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiVariable;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;

public class SimplifyVariableIntention extends Intention{
    public String getText(){
        return "Replace with Java-style array declaration";
    }

    public String getFamilyName(){
        return "Replace With Java Style Array Declaration";
    }

    public PsiElementPredicate getElementPredicate(){
        return new SimplifyVariablePredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiVariable var = (PsiVariable) element;
        var.normalizeDeclaration();
    }
}
