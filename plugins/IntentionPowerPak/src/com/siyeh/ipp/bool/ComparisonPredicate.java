package com.siyeh.ipp.bool;

import com.intellij.psi.*;
import com.siyeh.ipp.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ComparisonUtils;

class ComparisonPredicate implements PsiElementPredicate
{
    public boolean satisfiedBy(PsiElement element)
    {
        if(!(element instanceof PsiBinaryExpression))
        {
            return false;
        }
        final PsiBinaryExpression expression = (PsiBinaryExpression) element;

        final PsiExpression lhs = expression.getLOperand();
        final PsiExpression rhs = expression.getROperand();
        if(lhs == null || rhs == null)
        {
            return false;
        }
        return ComparisonUtils.isComparison((PsiExpression) element);
    }
}
