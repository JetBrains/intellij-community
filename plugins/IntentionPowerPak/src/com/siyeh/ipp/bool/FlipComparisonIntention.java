package com.siyeh.ipp.bool;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.*;
import com.siyeh.ipp.psiutils.ComparisonUtils;

public class FlipComparisonIntention extends MutablyNamedIntention
{
    public FlipComparisonIntention(Project project)
    {
        super(project);
    }

    public String getTextForElement(PsiElement element)
    {
        String operatorText = "";
        String flippedOperatorText = "";
        final PsiBinaryExpression exp = (PsiBinaryExpression) element;
        if(exp != null)
        {
            final PsiJavaToken sign = exp.getOperationSign();
            operatorText = sign.getText();
            flippedOperatorText = ComparisonUtils.getFlippedComparison(operatorText);
        }
        if(operatorText.equals(flippedOperatorText))
        {
            return "Flip " + operatorText;
        }
        else
        {
            return "Flip " + operatorText + " to " + flippedOperatorText;
        }
    }

    public String getFamilyName()
    {
        return "Flip Comparison";
    }

    public PsiElementPredicate getElementPredicate()
    {
        return new ComparisonPredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException
    {
        final PsiBinaryExpression exp = (PsiBinaryExpression) findMatchingElement(file, editor);
        final PsiExpression lhs = exp.getLOperand();
        final PsiExpression rhs = exp.getROperand();
        final PsiJavaToken sign = exp.getOperationSign();
        final String operand = sign.getText();
        final String expString = rhs.getText() + ComparisonUtils.getFlippedComparison(operand) + lhs.getText();
        replaceExpression(project, expString, exp);
    }
}
