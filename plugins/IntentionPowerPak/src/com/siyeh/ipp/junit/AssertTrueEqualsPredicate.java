package com.siyeh.ipp.junit;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;

class AssertTrueEqualsPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiMethodCallExpression)){
            return false;
        }
        final PsiMethodCallExpression expression =
                (PsiMethodCallExpression) element;
        final PsiExpressionList argumentList = expression.getArgumentList();
        if(argumentList == null){
            return false;
        }
        final PsiExpression[] args = argumentList.getExpressions();
        final int numArgs = args.length;
        if(numArgs < 1 || numArgs > 2){
            return false;
        }
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        if(methodExpression == null){
            return false;
        }
        final String methodName = methodExpression.getReferenceName();
        if(!"assertTrue".equals(methodName)){
            return false;
        }
        if(numArgs == 1){
            return isEquality(args[0]);
        } else{
            return isEquality(args[1]);
        }
    }

    private static boolean isEquality(PsiExpression arg){
        if(arg instanceof PsiBinaryExpression){
            final PsiBinaryExpression binaryExp = (PsiBinaryExpression) arg;
            final PsiJavaToken sign = binaryExp.getOperationSign();
            return sign.getTokenType() == JavaTokenType.EQEQ;
        } else if(arg instanceof PsiMethodCallExpression){
            final PsiMethodCallExpression expression =
                    (PsiMethodCallExpression) arg;
            if(expression.getArgumentList() == null){
                return false;
            }
            if(expression.getArgumentList().getExpressions().length != 1){
                return false;
            }
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            if(methodExpression == null){
                return false;
            }
            final String methodName = methodExpression.getReferenceName();
            return "equals".equals(methodName);
        }
        return false;
    }
}
