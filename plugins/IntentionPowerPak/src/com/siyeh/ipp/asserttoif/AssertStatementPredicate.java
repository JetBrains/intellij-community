package com.siyeh.ipp.asserttoif;

import com.intellij.psi.PsiAssertStatement;
import com.intellij.psi.PsiElement;
import com.siyeh.ipp.base.PsiElementPredicate;

class AssertStatementPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiAssertStatement)){
            return false;
        }
        return ((PsiAssertStatement) element).getAssertCondition() != null;
    }
}
