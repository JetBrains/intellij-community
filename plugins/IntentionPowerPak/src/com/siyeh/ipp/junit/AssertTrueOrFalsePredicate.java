package com.siyeh.ipp.junit;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiExpressionList;
import com.siyeh.ipp.base.PsiElementPredicate;

class AssertTrueOrFalsePredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiMethodCallExpression)){
            return false;
        }
        final PsiMethodCallExpression expression =
                (PsiMethodCallExpression) element;
        final PsiExpressionList argumentList = expression.getArgumentList();
        if(argumentList == null)
        {
            return false;
        }
        final int numExpressions = argumentList.getExpressions().length;
        if(numExpressions < 1 || numExpressions > 2){
            return false;
        }
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        if(methodExpression == null){
            return false;
        }
        final String methodName = methodExpression.getReferenceName();
        return "assertTrue".equals(methodName) ||
                "assertFalse".equals(methodName);
    }
}
