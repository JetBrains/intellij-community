package com.siyeh.ipp.junit;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;

class AssertLiteralPredicate implements PsiElementPredicate {

    AssertLiteralPredicate() {
        super();
    }

    public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof PsiMethodCallExpression)) {
            return false;
        }
        final PsiMethodCallExpression expression = (PsiMethodCallExpression) element;
        final int numExpressions = expression.getArgumentList().getExpressions().length;
        if (numExpressions < 1 || numExpressions > 2) {
            return false;
        }
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        if (methodExpression == null) {
            return false;
        }
        final String methodName = methodExpression.getReferenceName();

        return "assertTrue".equals(methodName) ||
                "assertFalse".equals(methodName) ||
                "assertNull".equals(methodName);
    }
}
