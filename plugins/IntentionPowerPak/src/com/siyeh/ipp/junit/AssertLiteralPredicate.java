package com.siyeh.ipp.junit;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;

class AssertLiteralPredicate implements PsiElementPredicate{
    AssertLiteralPredicate(){
        super();
    }

    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiMethodCallExpression)){
            return false;
        }
        if(ErrorUtil.containsError(element)){
            return false;
        }
        final PsiMethodCallExpression expression =
                (PsiMethodCallExpression) element;
        final PsiExpressionList args = expression.getArgumentList();
        if(args == null){
            return false;
        }
        final int numExpressions = args.getExpressions().length;
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
                "assertFalse".equals(methodName) ||
                "assertNull".equals(methodName);
    }
}
