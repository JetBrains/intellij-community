package com.siyeh.ipp.equality;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.*;
import com.siyeh.ipp.psiutils.ParenthesesUtils;

public class ReplaceEqualityWithSafeEqualsIntention extends Intention
{
    public ReplaceEqualityWithSafeEqualsIntention(Project project)
    {
        super(project);
    }

    public String getText()
    {
        return "Replace == with safe .equals()";
    }

    public String getFamilyName()
    {
        return "Replace Equality With Safe Equals";
    }

    public PsiElementPredicate getElementPredicate()
    {
        return new ObjectEqualityPredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException
    {
        //TODO: create reasonable semantics for this for negated equality
        final PsiBinaryExpression exp = (PsiBinaryExpression) findMatchingElement(file, editor);
        final PsiExpression lhs = exp.getLOperand();
        final PsiExpression rhs = exp.getROperand();
        final PsiExpression strippedLhs = ParenthesesUtils.stripParentheses(lhs);
        final PsiExpression strippedRhs = ParenthesesUtils.stripParentheses(rhs);
        final String expString;
        final String lhsText = strippedLhs.getText();
        final String rhsText = strippedRhs.getText();
        if(ParenthesesUtils.getPrecendence(strippedLhs) > ParenthesesUtils.METHOD_CALL_PRECEDENCE)
        {
            expString = lhsText + "==null?" + rhsText + " == null:(" + lhsText + ").equals(" + rhsText + ')';
        }
        else
        {
            expString = lhsText + "==null?" + rhsText + " == null:" + lhsText + ".equals(" + rhsText + ')';
        }
        replaceExpression(project, expString, exp);
    }
}
