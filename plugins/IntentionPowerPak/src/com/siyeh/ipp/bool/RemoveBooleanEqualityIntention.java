package com.siyeh.ipp.bool;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.*;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;

public class RemoveBooleanEqualityIntention extends MutablyNamedIntention
{

    protected String getTextForElement(PsiElement element)
    {
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) element;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        return "Simplify " + sign.getText();
    }

    public String getFamilyName()
    {
        return "Remove Boolean Equality";
    }

    public PsiElementPredicate getElementPredicate()
    {
        return new BooleanLiteralEqualityPredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException
    {
        final PsiBinaryExpression exp =
                (PsiBinaryExpression) findMatchingElement(file, editor);
        final PsiJavaToken sign = exp.getOperationSign();
        final boolean isEquals = sign.getTokenType() == JavaTokenType.EQEQ;
        final PsiExpression lhs = exp.getLOperand();
        final String lhsText = lhs.getText();
        final PsiExpression rhs = exp.getROperand();
        final String rhsText = rhs.getText();
        if("true".equals(lhsText))
        {
            if(isEquals)
            {
                replaceExpression(project, rhsText, exp);
            }
            else
            {
                replaceExpressionWithNegatedExpression(project, rhs, exp);
            }
        }
        else if("false".equals(lhsText))
        {
            if(isEquals)
            {
                replaceExpressionWithNegatedExpression(project, rhs, exp);
            }
            else
            {
                replaceExpression(project, rhsText, exp);
            }
        }
        else if("true".equals(rhsText))
        {
            if(isEquals)
            {
                replaceExpression(project, lhsText, exp);
            }
            else
            {
                replaceExpressionWithNegatedExpression(project, lhs, exp);
            }
        }
        else
        {
            if(isEquals)
            {
                replaceExpressionWithNegatedExpression(project, lhs, exp);
            }
            else
            {
                replaceExpression(project, lhsText, exp);
            }
        }

    }
}
