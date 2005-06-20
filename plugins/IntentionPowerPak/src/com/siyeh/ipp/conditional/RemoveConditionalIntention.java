package com.siyeh.ipp.conditional;

import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.BoolUtils;
import org.jetbrains.annotations.NotNull;

public class RemoveConditionalIntention extends Intention{
    public String getText(){
        return "Simplify ?:";
    }

    public String getFamilyName(){
        return "Remove Pointless Conditional";
    }

    @NotNull
    public PsiElementPredicate getElementPredicate(){
        return new RemoveConditionalPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiConditionalExpression exp = (PsiConditionalExpression) element;
        final PsiExpression condition = exp.getCondition();
        final PsiExpression thenExpression = exp.getThenExpression();
        assert thenExpression != null;
        final String thenExpressionText = thenExpression.getText();
        if("true".equals(thenExpressionText)){
            final String newExpression = condition.getText();
            replaceExpression(newExpression, exp);
        } else{
            final String newExpression =
                    BoolUtils.getNegatedExpressionText(condition);
            replaceExpression(newExpression, exp);
        }
    }
}
