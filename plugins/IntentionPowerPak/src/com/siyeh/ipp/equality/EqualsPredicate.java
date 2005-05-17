package com.siyeh.ipp.equality;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;

class EqualsPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiMethodCallExpression)){
            return false;
        }
        if(ErrorUtil.containsError(element)){
            return false;
        }
        final PsiMethodCallExpression expression =
                (PsiMethodCallExpression) element;
        final PsiExpressionList argumentList = expression.getArgumentList();
        if(argumentList == null){
            return false;
        }
        final PsiExpression[] args = argumentList.getExpressions();
        if(args.length != 1){
            return false;
        }
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        if(methodExpression == null){
            return false;
        }
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if(qualifier == null){
            return false;
        }
        final String methodName = methodExpression.getReferenceName();
        return "equals".equals(methodName);
    }
}
