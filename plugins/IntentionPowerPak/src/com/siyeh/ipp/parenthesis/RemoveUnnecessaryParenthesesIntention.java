package com.siyeh.ipp.parenthesis;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.*;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ParenthesesUtils;

public class RemoveUnnecessaryParenthesesIntention extends Intention
{

    public String getText()
    {
        return "Remove unnecessary parentheses";
    }

    public String getFamilyName()
    {
        return "Remove Unnecessary Parentheses";
    }

    public PsiElementPredicate getElementPredicate()
    {
        return new UnnecessaryParenthesesPredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException
    {
        PsiExpression exp =
                (PsiExpression) findMatchingElement(file, editor);
        while(exp.getParent() instanceof PsiExpression)
        {
            exp = (PsiExpression) exp.getParent();
        }
        final String newExpression = ParenthesesUtils.removeParentheses(exp);
        replaceExpression(project, newExpression, exp);
    }

}
