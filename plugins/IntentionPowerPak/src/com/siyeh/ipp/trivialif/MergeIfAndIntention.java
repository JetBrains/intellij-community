package com.siyeh.ipp.trivialif;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.Intention;
import com.siyeh.ipp.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ConditionalUtils;
import com.siyeh.ipp.psiutils.ParenthesesUtils;

public class MergeIfAndIntention extends Intention
{
    public MergeIfAndIntention(Project project)
    {
        super(project);
    }

    public String getText()
    {
        return "Merge 'if's";
    }

    public String getFamilyName()
    {
        return "Merge Nested Ifs To ANDed Condition";
    }

    public PsiElementPredicate getElementPredicate()
    {
        return new MergeIfAndPredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException
    {
        final PsiJavaToken token = (PsiJavaToken) findMatchingElement(file, editor);
        final PsiIfStatement parentStatement = (PsiIfStatement) token.getParent();
        final PsiStatement parentThenBranch = parentStatement.getThenBranch();
        final PsiIfStatement childStatement = (PsiIfStatement) ConditionalUtils.stripBraces(parentThenBranch);

        final String childConditionText;
        final PsiExpression childCondition = childStatement.getCondition();
        if(ParenthesesUtils.getPrecendence(childCondition)
                > ParenthesesUtils.AND_PRECEDENCE)
        {
            childConditionText = '(' + childCondition.getText() + ')';
        }
        else
        {
            childConditionText = childCondition.getText();

        }
        final String parentConditionText;

        final PsiExpression parentCondition = parentStatement.getCondition();
        if(ParenthesesUtils.getPrecendence(parentCondition)
                > ParenthesesUtils.AND_PRECEDENCE)
        {
            parentConditionText = '(' + parentCondition.getText() + ')';
        }
        else
        {
            parentConditionText = parentCondition.getText();
        }

        final PsiStatement childThenBranch = childStatement.getThenBranch();
        final String statement = "if(" + parentConditionText + "&&" + childConditionText + ')' +
                childThenBranch.getText();
        replaceStatement(project, statement, parentStatement);
    }
}