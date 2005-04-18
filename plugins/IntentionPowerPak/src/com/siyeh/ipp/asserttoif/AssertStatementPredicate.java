package com.siyeh.ipp.asserttoif;

import com.intellij.psi.PsiAssertStatement;
import com.intellij.psi.PsiElement;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;

class AssertStatementPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiAssertStatement)){
            return false;
        }
        if(ErrorUtil.containsError(element)){
            return false;
        }
        return ((PsiAssertStatement) element).getAssertCondition() != null;
    }
}
