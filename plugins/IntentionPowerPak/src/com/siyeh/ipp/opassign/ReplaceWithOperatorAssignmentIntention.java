package com.siyeh.ipp.opassign;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.*;

public class ReplaceWithOperatorAssignmentIntention extends MutablyNamedIntention
{
    public ReplaceWithOperatorAssignmentIntention(Project project)
    {
        super(project);
    }

    public String getTextForElement(PsiElement element)
    {
        final PsiAssignmentExpression exp = (PsiAssignmentExpression) element;
        final PsiBinaryExpression rhs = (PsiBinaryExpression) exp.getRExpression();
        final PsiJavaToken sign = rhs.getOperationSign();
        final String operator = sign.getText();
        return "Replace = with " + operator + '=';
    }

    public String getFamilyName()
    {
        return "Replace Assignment With Operator Assignment";
    }

    public PsiElementPredicate getElementPredicate()
    {
        return new AssignmentExpressionReplaceableWithOperatorAssigment();
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException
    {
        final PsiAssignmentExpression exp = (PsiAssignmentExpression) findMatchingElement(file, editor);
        final PsiBinaryExpression rhs = (PsiBinaryExpression) exp.getRExpression();
        final PsiExpression lhs = exp.getLExpression();
        final PsiJavaToken sign = rhs.getOperationSign();
        final String operand = sign.getText();
        final PsiExpression rhsrhs = rhs.getROperand();
        final String expString = lhs.getText() + operand + '=' + rhsrhs.getText();
        replaceExpression(project, expString, exp);
    }
}
