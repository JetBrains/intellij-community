package com.siyeh.ipp.bool;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.*;
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

    public void invoke(Project project, Editor editor, PsiFile file)
            throws IncorrectOperationException{
        final PsiBinaryExpression exp =
                (PsiBinaryExpression) findMatchingElement(file, editor);
        final PsiExpression lhs = exp.getLOperand();
        final PsiExpression rhs = exp.getROperand();
        final PsiJavaToken sign = exp.getOperationSign();
        final String operator = sign.getText();
        final String negatedOperator =
                ComparisonUtils.getNegatedComparison(operator);
        replaceExpressionWithNegatedExpressionString(project,
                                                     lhs.getText() +
                                                             negatedOperator +
                                                             rhs.getText(),
                                                     exp);
    }
}
