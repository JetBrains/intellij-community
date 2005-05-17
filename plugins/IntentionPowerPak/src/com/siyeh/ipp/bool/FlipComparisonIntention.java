package com.siyeh.ipp.bool;

import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ComparisonUtils;

public class FlipComparisonIntention extends MutablyNamedIntention{
    public String getTextForElement(PsiElement element){
        String operatorText = "";
        String flippedOperatorText = "";
        final PsiBinaryExpression exp = (PsiBinaryExpression) element;
        if(exp != null){
            final PsiJavaToken sign = exp.getOperationSign();
            operatorText = sign.getText();
            flippedOperatorText = ComparisonUtils.getFlippedComparison(operatorText);
        }
        if(operatorText.equals(flippedOperatorText)){
            return "Flip " + operatorText;
        } else{
            return "Flip " + operatorText + " to " + flippedOperatorText;
        }
    }

    public String getFamilyName(){
        return "Flip Comparison";
    }

    public PsiElementPredicate getElementPredicate(){
        return new ComparisonPredicate();
    }

    public void processIntention(PsiElement element)
        throws IncorrectOperationException {
        final PsiBinaryExpression exp =
                (PsiBinaryExpression) element;
        final PsiExpression lhs = exp.getLOperand();
        final PsiExpression rhs = exp.getROperand();
        final PsiJavaToken sign = exp.getOperationSign();
        final String operand = sign.getText();
        final String expString =
                rhs.getText() + ComparisonUtils.getFlippedComparison(operand) +
                        lhs.getText();
        replaceExpression(expString, exp);
    }
}
