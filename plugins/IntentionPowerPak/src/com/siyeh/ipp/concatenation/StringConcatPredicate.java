package com.siyeh.ipp.concatenation;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.PsiElementPredicate;

class StringConcatPredicate implements PsiElementPredicate {
    public boolean satisfiedBy(PsiElement element) {
        if (element instanceof PsiJavaToken) {
            final PsiJavaToken token = (PsiJavaToken) element;
            final IElementType tokenType = token.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUS)) {
                return false;
            }
        } else if (!(element instanceof PsiWhiteSpace)) {
            return false;
        }
        final PsiElement parent = element.getParent();
        if (!(parent instanceof PsiBinaryExpression)) {
            return false;
        }
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) parent;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        if (sign == null) {
            return false;
        }
        final IElementType tokenType = sign.getTokenType();
        if (!tokenType.equals(JavaTokenType.PLUS)) {
            return false;
        }
        final PsiExpression rOperand = binaryExpression.getROperand();
        if (!(rOperand instanceof PsiLiteralExpression)) {
            return false;
        }
        final PsiExpression lhs = binaryExpression.getLOperand();
        if (!isLeftSideLiteral(lhs)) {
            return false;
        }
        return true;
    }

    private boolean isLeftSideLiteral(PsiExpression expression) {
        if (expression instanceof PsiBinaryExpression) {
            final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) expression;
            final PsiExpression rhs = binaryExpression.getROperand();
            return isLeftSideLiteral(rhs);
        }
        return expression instanceof PsiLiteralExpression;
    }
}
