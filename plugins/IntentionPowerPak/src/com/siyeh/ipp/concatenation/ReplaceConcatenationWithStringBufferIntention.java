package com.siyeh.ipp.concatenation;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.*;
import com.siyeh.ipp.psiutils.ParenthesesUtils;

public class ReplaceConcatenationWithStringBufferIntention extends Intention
{
    public ReplaceConcatenationWithStringBufferIntention(Project project)
    {
        super(project);
    }

    public String getText()
    {
        return "Replace + with .append()";
    }

    public String getFamilyName()
    {
        return "Replace + with StringBuffer.append()";
    }

    public PsiElementPredicate getElementPredicate()
    {
        return new ReplaceConcatenationWithStringBufferPredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException
    {
        PsiBinaryExpression exp =
                (PsiBinaryExpression) findMatchingElement(file, editor);
        PsiElement parent = exp.getParent();
        while(ConcatenationUtils.isConcatenation(parent))
        {
            exp = (PsiBinaryExpression) parent;
            parent = exp.getParent();
        }
        final String text = exp.getText();
        final StringBuffer expString = new StringBuffer(text.length() * 3);
        if (isPartOfStringBufferAppend(exp)) {
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression)parent.getParent();
            final PsiExpression qualifierExpression =
                    methodCallExpression.getMethodExpression().getQualifierExpression();
            final String qualifierText = qualifierExpression.getText();
            expString.append(qualifierText);
            turnExpressionIntoChainedAppends(exp, expString);
            final String newExpression = expString.toString();
            replaceExpression(project, newExpression, methodCallExpression);
        } else {
            expString.append("new StringBuffer()");
            turnExpressionIntoChainedAppends(exp, expString);
            expString.append(".toString()");
            final String newExpression = expString.toString();
            replaceExpression(project, newExpression, exp);
        }
    }

    private static boolean isPartOfStringBufferAppend(PsiExpression exp)
    {
        PsiElement parent = exp.getParent();
        if (!(parent instanceof PsiExpressionList))
        {
            return false;
        }
        parent = parent.getParent();
        if (!(parent instanceof PsiMethodCallExpression))
        {
            return false;
        }
        final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)parent;
        final PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
        final PsiType type = methodExpression.getType();
        final String className = type.getCanonicalText();
        if (!"java.lang.StringBuffer".equals(className) &&
                !"java.lang.StringBuilder".equals(className))
        {
            return false;
        }
        final String methodName = methodExpression.getReferenceName();
        if (!"append".equals(methodName))
        {
            return false;
        }
        return true;
    }

    private static void turnExpressionIntoChainedAppends(PsiExpression exp, StringBuffer expString)
    {
        if(ConcatenationUtils.isConcatenation(exp))
        {
            final PsiBinaryExpression concat = (PsiBinaryExpression) exp;
            final PsiExpression lhs = concat.getLOperand();
            turnExpressionIntoChainedAppends(lhs, expString);
            final PsiExpression rhs = concat.getROperand();
            turnExpressionIntoChainedAppends(rhs, expString);
        }
        else
        {
            final PsiExpression strippedExpression = ParenthesesUtils.stripParentheses(exp);
            expString.append(".append(" + strippedExpression.getText() + ')');
        }
    }
}
