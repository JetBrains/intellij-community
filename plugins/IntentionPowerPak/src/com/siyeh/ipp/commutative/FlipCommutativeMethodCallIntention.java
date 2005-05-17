package com.siyeh.ipp.commutative;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ParenthesesUtils;

public class FlipCommutativeMethodCallIntention extends MutablyNamedIntention{
    protected String getTextForElement(PsiElement element){
        final PsiMethodCallExpression call = (PsiMethodCallExpression) element;
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        final String methodName = methodExpression.getReferenceName();
        return "Flip ." + methodName + "()";
    }

    public String getFamilyName(){
        return "Flip Commutative Method Call";
    }

    public PsiElementPredicate getElementPredicate(){
        return new FlipCommutativeMethodCallPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiMethodCallExpression call =
                (PsiMethodCallExpression) element;
        assert call != null;
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        final String methodName = methodExpression.getReferenceName();
        final PsiExpression target = methodExpression.getQualifierExpression();
        final PsiExpressionList argumentList = call.getArgumentList();
        assert argumentList != null;
        final PsiExpression arg = argumentList.getExpressions()[0];
        final PsiExpression strippedTarget =
                ParenthesesUtils.stripParentheses(target);
        final PsiExpression strippedArg =
                ParenthesesUtils.stripParentheses(arg);
        final String callString;
        if(ParenthesesUtils.getPrecendence(strippedArg) >
                ParenthesesUtils.METHOD_CALL_PRECEDENCE){
            callString = '(' + strippedArg.getText() + ")." + methodName + '(' +
                    strippedTarget.getText() + ')';
        } else{
            callString = strippedArg.getText() + '.' + methodName + '(' +
                    strippedTarget.getText() + ')';
        }
        replaceExpression(callString, call);
    }
}
