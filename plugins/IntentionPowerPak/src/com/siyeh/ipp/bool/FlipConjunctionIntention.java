package com.siyeh.ipp.bool;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.*;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;

public class FlipConjunctionIntention extends MutablyNamedIntention
{

    protected String getTextForElement(PsiElement element)
    {
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) element;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        return "Flip " + sign.getText();
    }

    public String getFamilyName()
    {
        return "Flip Conjunction Operands";
    }

    public PsiElementPredicate getElementPredicate()
    {
        return new ConjunctionPredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException
    {
        PsiExpression exp = (PsiExpression) findMatchingElement(file, editor);
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) exp;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final IElementType conjunctionType = sign.getTokenType();
        PsiElement parent = exp.getParent();
        while(isConjunctionExpression(parent, conjunctionType))
        {
            exp = (PsiExpression) parent;
            parent = exp.getParent();
        }
        final String newExpression = flipExpression(exp, conjunctionType);
        replaceExpression(project, newExpression, exp);
    }

    private String flipExpression(PsiExpression exp, IElementType conjunctionType)
    {
        if(isConjunctionExpression(exp, conjunctionType))
        {
            final PsiBinaryExpression andExpression = (PsiBinaryExpression) exp;

            final PsiExpression rhs = andExpression.getROperand();
            final PsiExpression lhs = andExpression.getLOperand();
            final String conjunctionSign;
            if(conjunctionType.equals(JavaTokenType.ANDAND))
            {
                conjunctionSign = "&&";
            }
            else
            {
                conjunctionSign = "||";

            }
            return flipExpression(rhs, conjunctionType) + ' ' + conjunctionSign + ' ' + flipExpression(lhs, conjunctionType);
        }
        else
        {
            return exp.getText();
        }
    }

    private static boolean isConjunctionExpression(PsiElement exp, IElementType conjunctionType)
    {
        if(!(exp instanceof PsiBinaryExpression))
        {
            return false;
        }
        final PsiBinaryExpression binExp = (PsiBinaryExpression) exp;
        final PsiJavaToken sign = binExp.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        return tokenType.equals(conjunctionType);
    }
}
