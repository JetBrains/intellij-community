package com.siyeh.ipp.trivialif;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.Intention;
import com.siyeh.ipp.PsiElementPredicate;

public class SplitElseIfIntention extends Intention
{
    public SplitElseIfIntention(Project project)
    {
        super(project);
    }

    public String getText()
    {
        return "Split else-if";
    }

    public String getFamilyName()
    {
        return "Split Else If";
    }

    public PsiElementPredicate getElementPredicate()
    {
        return new SplitElseIfPredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException
    {
        final PsiJavaToken token = (PsiJavaToken) findMatchingElement(file, editor);
        final PsiIfStatement parentStatement = (PsiIfStatement) token.getParent();
        final PsiStatement thenBranch = parentStatement.getThenBranch();
        final PsiStatement elseBranch = parentStatement.getElseBranch();
        final PsiExpression condition = parentStatement.getCondition();

        final String newStatement = "if(" + condition.getText() + ')' +thenBranch.getText() + "else{" + elseBranch.getText() + '}';

        replaceStatement(project, newStatement, parentStatement);
    }
}