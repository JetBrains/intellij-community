package com.siyeh.ipp.conditional;

import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.siyeh.ipp.PsiElementPredicate;

class FlipConditionalPredicate implements PsiElementPredicate
{
    public boolean satisfiedBy(PsiElement element)
    {
        if(!(element instanceof PsiConditionalExpression))
        {
            return false;
        }
        final PsiConditionalExpression condition = (PsiConditionalExpression) element;
   
        return condition.getCondition() != null &&
                condition.getThenExpression() != null &&
                condition.getElseExpression() != null;

    }
}
