package com.siyeh.ipp.trivialif;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;

public class ExpandBooleanIntention extends Intention{
    public String getText(){
        return "Expand boolean use to if-else";
    }

    public String getFamilyName(){
        return "Expand Boolean";
    }

    public PsiElementPredicate getElementPredicate(){
        return new ExpandBooleanPredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file)
            throws IncorrectOperationException{
        final PsiJavaToken token =
                (PsiJavaToken) findMatchingElement(file, editor);
        final PsiStatement containingStatement =
                (PsiStatement) PsiTreeUtil.getParentOfType(token,
                                                           PsiStatement.class);

        if(ExpandBooleanPredicate.isBooleanAssignment(containingStatement)){
            final PsiExpressionStatement assignmentStatement =
                    (PsiExpressionStatement) containingStatement;
            final PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression) assignmentStatement.getExpression();
            final PsiExpression rhs = assignmentExpression.getRExpression();
            final String rhsText = rhs.getText();
            final PsiExpression lhs = assignmentExpression.getLExpression();
            final String lhsText = lhs.getText();
            final String statement =
            "if(" + rhsText + "){" + lhsText + " = true;}else{" + lhsText +
                    " = false;}";
            replaceStatement(project, statement, containingStatement);
        } else if(ExpandBooleanPredicate.isBooleanReturn(containingStatement)){
            final PsiReturnStatement returnStatement =
                    (PsiReturnStatement) containingStatement;
            final PsiExpression returnValue = returnStatement.getReturnValue();
            final String valueText = returnValue.getText();
            final String statement =
            "if(" + valueText + "){return true;}else{return false;}";
            replaceStatement(project, statement, containingStatement);
        }
    }
}