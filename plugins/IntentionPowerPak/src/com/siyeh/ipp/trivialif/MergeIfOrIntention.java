package com.siyeh.ipp.trivialif;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ParenthesesUtils;

public class MergeIfOrIntention extends Intention
{

    public String getText()
    {
        return "Merge 'if's";
    }

    public String getFamilyName()
    {
        return "Merge Equivalent Ifs To ORed Condition";
    }

    public PsiElementPredicate getElementPredicate()
    {
        return new MergeIfOrPredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException
    {
        final PsiJavaToken token = (PsiJavaToken) findMatchingElement(file, editor);
        final PsiIfStatement parentStatement = (PsiIfStatement) token.getParent();
        final PsiIfStatement childStatement = (PsiIfStatement) parentStatement.getElseBranch();

        final String childConditionText;
        final PsiExpression childCondition = childStatement.getCondition();
        if(ParenthesesUtils.getPrecendence(childCondition)
                > ParenthesesUtils.OR_PRECEDENCE)
        {
            childConditionText = '(' + childCondition.getText() + ')';
        }
        else
        {
            childConditionText = childCondition.getText();
        }

        final String parentConditionText;
        final PsiExpression condition = parentStatement.getCondition();
        if(ParenthesesUtils.getPrecendence(condition)
                > ParenthesesUtils.OR_PRECEDENCE)
        {
            parentConditionText = '(' + condition.getText() + ')';
        }
        else
        {
            parentConditionText = condition.getText();
        }

        final PsiStatement parentThenBranch = parentStatement.getThenBranch();
        String statement = "if(" + parentConditionText + "||" + childConditionText + ')' +
                parentThenBranch.getText();
        final PsiStatement childElseBranch = childStatement.getElseBranch();
        if(childElseBranch != null)
        {
            statement += "else " + childElseBranch.getText();
        }
        replaceStatement(project, statement, parentStatement);
    }
}