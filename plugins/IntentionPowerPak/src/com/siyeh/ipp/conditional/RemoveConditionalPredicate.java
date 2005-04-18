package com.siyeh.ipp.conditional;

import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import com.siyeh.ipp.psiutils.ErrorUtil;

class RemoveConditionalPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiConditionalExpression)){
            return false;
        }
        if(ErrorUtil.containsError(element)){
            return false;
        }
        final PsiConditionalExpression condition =
                (PsiConditionalExpression) element;

        PsiExpression thenExpression = condition.getThenExpression();
        PsiExpression elseExpression = condition.getElseExpression();
        if(condition.getCondition() == null ||
                   thenExpression == null ||
                   elseExpression == null){
            return false;
        }

        thenExpression = ParenthesesUtils.stripParentheses(thenExpression);
        elseExpression = ParenthesesUtils.stripParentheses(elseExpression);
        if(thenExpression == null ||
                   elseExpression == null){
            return false;
        }
        final String thenText = thenExpression.getText();
        final String elseText = elseExpression.getText();
        if("true".equals(elseText) && "false".equals(thenText)){
            return true;
        } else if("true".equals(thenText) && "false".equals(elseText)){
            return true;
        }
        return false;
    }
}
