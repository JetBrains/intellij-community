package com.siyeh.ipp.conditional;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.BoolUtils;

public class FlipConditionalIntention extends Intention
{

    public String getText()
    {
        return "Flip ?:";
    }

    public String getFamilyName()
    {
        return "Flip Conditional";
    }

    public PsiElementPredicate getElementPredicate()
    {
        return new FlipConditionalPredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException
    {
        final PsiConditionalExpression exp = (PsiConditionalExpression) findMatchingElement(file, editor);

        final PsiExpression condition = exp.getCondition();
        final PsiExpression elseExpression = exp.getElseExpression();
        final PsiExpression thenExpression = exp.getThenExpression();
        final String newExpression = BoolUtils.getNegatedExpressionText(condition) + '?' +
                elseExpression.getText() +
                ':' +
                thenExpression.getText();
        replaceExpression(project, newExpression, exp);
    }

}
