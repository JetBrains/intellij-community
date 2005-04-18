package com.siyeh.ipp.concatenation;

import com.intellij.psi.PsiElement;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;

class ReplaceConcatenationWithStringBufferPredicate
        implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!ConcatenationUtils.isConcatenation(element)){
            return false;
        }
        return !ErrorUtil.containsError(element);
    }
}
