package com.siyeh.ipp.concatenation;

import com.intellij.psi.PsiElement;
import com.siyeh.ipp.PsiElementPredicate;

class ReplaceConcatenationWithStringBufferPredicate implements PsiElementPredicate
{
    public boolean satisfiedBy(PsiElement element)
    {
        return ConcatenationUtils.isConcatenation(element);
    }

}
