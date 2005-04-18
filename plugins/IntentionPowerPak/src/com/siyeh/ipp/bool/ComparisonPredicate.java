package com.siyeh.ipp.bool;

import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ComparisonUtils;
import com.siyeh.ipp.psiutils.ErrorUtil;

class ComparisonPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiBinaryExpression)){
            return false;
        }
        if(ErrorUtil.containsError(element)){
            return false;
        }
        final PsiBinaryExpression expression = (PsiBinaryExpression) element;

        final PsiExpression lhs = expression.getLOperand();
        final PsiExpression rhs = expression.getROperand();
        if(lhs == null || rhs == null){
            return false;
        }
        return ComparisonUtils.isComparison((PsiExpression) element);
    }
}
