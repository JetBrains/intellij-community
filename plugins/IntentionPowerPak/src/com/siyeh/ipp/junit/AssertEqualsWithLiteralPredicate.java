package com.siyeh.ipp.junit;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;

class AssertEqualsWithLiteralPredicate implements PsiElementPredicate{
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
        if(numArgs < 2 || numArgs > 3){
            return false;
        }
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        if(methodExpression == null){
            return false;
        }
        final String methodName = methodExpression.getReferenceName();
        if(!"assertEquals".equals(methodName)){
            return false;
        }
        if(numArgs == 2){
            return isSpecialLiteral(args[0]) || isSpecialLiteral(args[1]);
        } else{
            return isSpecialLiteral(args[1]) || isSpecialLiteral(args[2]);
        }
    }

    private boolean isSpecialLiteral(PsiExpression arg){
        if(arg == null){
            return false;
        }
        final String text = arg.getText();
        return "true".equals(text) ||
                "false".equals(text) || "null".equals(text);
    }
}
