package com.siyeh.ipp.bool;

import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ComparisonUtils;

public class NegateComparisonIntention extends MutablyNamedIntention{
    public String getTextForElement(PsiElement element){
        String operatorText = "";
        String negatedOperatorText = "";
        final PsiBinaryExpression exp = (PsiBinaryExpression) element;
        if(exp != null){
            final PsiJavaToken sign = exp.getOperationSign();
            operatorText = sign.getText();
            negatedOperatorText = ComparisonUtils.getNegatedComparison(operatorText);
        }
        if(operatorText.equals(negatedOperatorText)){
            return "Negate " + operatorText;
        } else{
            return "Negate " + operatorText + " to " + negatedOperatorText;
        }
    }

    public String getFamilyName(){
        return "Negate Comparison";
    }

    public PsiElementPredicate getElementPredicate(){
        return new ComparisonPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiBinaryExpression exp =
                (PsiBinaryExpression) element;
        final PsiExpression lhs = exp.getLOperand();
        final PsiExpression rhs = exp.getROperand();
        final PsiJavaToken sign = exp.getOperationSign();
        final String operator = sign.getText();
        final String negatedOperator =
                ComparisonUtils.getNegatedComparison(operator);
        final String lhsText = lhs.getText();
        assert rhs != null;
        final String rhsText = rhs.getText();
        replaceExpressionWithNegatedExpressionString(lhsText +
                negatedOperator +
                rhsText, exp);
    }
}
