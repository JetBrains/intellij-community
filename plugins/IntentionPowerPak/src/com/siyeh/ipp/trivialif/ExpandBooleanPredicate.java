package com.siyeh.ipp.trivialif;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ipp.base.PsiElementPredicate;

class ExpandBooleanPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiJavaToken)){
            return false;
        }
        final PsiJavaToken token = (PsiJavaToken) element;
        final PsiStatement containingStatement =
                (PsiStatement) PsiTreeUtil.getParentOfType(token,
                                                           PsiStatement.class);
        if(containingStatement == null){
            return false;
        }
        if(isBooleanReturn(containingStatement)){
            return true;
        }
        if(isBooleanAssignment(containingStatement)){
            return true;
        }
        return false;
    }

    public static boolean isBooleanReturn(PsiStatement containingStatement){
        if(!(containingStatement instanceof PsiReturnStatement)){
            return false;
        }
        final PsiReturnStatement returnStatement =
                (PsiReturnStatement) containingStatement;
        final PsiExpression returnValue = returnStatement.getReturnValue();
        if(returnValue == null){
            return false;
        }
        if(returnValue instanceof PsiLiteralExpression)
        {
            return false;
        }
        final PsiType returnType = returnValue.getType();
        if(returnType == null){
            return false;
        }
        return returnType.equals(PsiType.BOOLEAN);
    }

    public static boolean isBooleanAssignment(PsiStatement containingStatement){
        if(!(containingStatement instanceof PsiExpressionStatement)){
            return false;
        }
        final PsiExpressionStatement expressionStatement =
                (PsiExpressionStatement) containingStatement;
        final PsiExpression expression = expressionStatement.getExpression();
        if(expression == null){
            return false;
        }
        if(!(expression instanceof PsiAssignmentExpression)){
            return false;
        }
        final PsiAssignmentExpression assignment =
                (PsiAssignmentExpression) expression;
        final PsiExpression rhs = assignment.getRExpression();
        if(rhs == null){
            return false;
        }
        if(rhs instanceof PsiLiteralExpression){
            return false;
        }
        final PsiType assignmentType = rhs.getType();
        if(assignmentType == null){
            return false;
        }
        return assignmentType.equals(PsiType.BOOLEAN);
    }
}
