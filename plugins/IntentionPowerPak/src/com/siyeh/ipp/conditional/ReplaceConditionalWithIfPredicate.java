package com.siyeh.ipp.conditional;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;

class ReplaceConditionalWithIfPredicate implements PsiElementPredicate {
    public boolean satisfiedBy(PsiElement element) {
        if (element instanceof PsiReturnStatement) {
            return isReturnOfConditional((PsiReturnStatement) element);

        }
        if (element instanceof PsiExpressionStatement) {
            return isAssignmentToConditional((PsiExpressionStatement) element);

        }
        if (element instanceof PsiDeclarationStatement) {
            return isDeclarationOfConditional((PsiDeclarationStatement) element);
        } else {
            return false;
        }
    }

    private static boolean isDeclarationOfConditional(PsiDeclarationStatement declStatement) {
        final PsiElement[] variables = declStatement.getDeclaredElements();
        if (variables.length != 1) {
            return false;
        }
        if (!(variables[0] instanceof PsiLocalVariable)) {
            return false;
        }
        final PsiLocalVariable var = (PsiLocalVariable) variables[0];
        final PsiExpression initializer = var.getInitializer();
        if (initializer == null) {
            return false;
        }
        if (!(initializer instanceof PsiConditionalExpression)) {
            return false;
        }
        final PsiConditionalExpression condition = (PsiConditionalExpression) initializer;

        if (condition.getCondition() == null ||
                condition.getThenExpression() == null ||
                condition.getElseExpression() == null) {
            return false;
        }
        return true;
    }

    private static boolean isAssignmentToConditional(PsiExpressionStatement expressionStatement) {
        if (!(expressionStatement.getExpression() instanceof PsiAssignmentExpression)) {
            return false;
        }
        final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) expressionStatement.getExpression();
        final PsiExpression lhs = assignmentExpression.getLExpression();
        final PsiExpression rhs = assignmentExpression.getRExpression();
        if (lhs == null || rhs == null) {
            return false;
        }
        if (!(rhs instanceof PsiConditionalExpression)) {
            return false;
        }
        final PsiConditionalExpression condition = (PsiConditionalExpression) rhs;

        if (condition.getCondition() == null ||
                condition.getThenExpression() == null ||
                condition.getElseExpression() == null) {
            return false;
        }
        return true;
    }

    private static boolean isReturnOfConditional(PsiReturnStatement returnStatement) {
        final PsiExpression returnValue = returnStatement.getReturnValue();
        if (returnValue == null) {
            return false;
        }
        if (!(returnValue instanceof PsiConditionalExpression)) {
            return false;
        }
        final PsiConditionalExpression condition = (PsiConditionalExpression) returnValue;

        return condition.getCondition() != null &&
                condition.getThenExpression() != null &&
                condition.getElseExpression() != null;
    }
}
