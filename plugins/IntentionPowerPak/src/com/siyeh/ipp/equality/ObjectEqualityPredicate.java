package com.siyeh.ipp.equality;

import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.PsiElementPredicate;

class ObjectEqualityPredicate implements PsiElementPredicate
{
    public boolean satisfiedBy(PsiElement element)
    {
        if (!(element instanceof PsiBinaryExpression)) {
            return false;
        }
        final PsiBinaryExpression expression = (PsiBinaryExpression) element;
        final PsiJavaToken sign = expression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if (!tokenType.equals(JavaTokenType.NE) &&
                !tokenType.equals(JavaTokenType.EQEQ)) {
            return false;
        }
        final PsiExpression lhs = expression.getLOperand();
        if (lhs == null) {
            return false;
        }
        final PsiType lhsType = lhs.getType();
        if (lhsType == null) {
            return false;
        }
        final PsiExpression rhs = expression.getROperand();
        if (rhs == null) {
            return false;
        }
        final PsiType rhsType = rhs.getType();
        if (rhsType == null) {
            return false;
        }
        return !TypeConversionUtil.isPrimitiveAndNotNull(lhsType) &&
                !TypeConversionUtil.isPrimitiveAndNotNull(rhsType);
    }

    private static boolean isPrimitive(final PsiType lhsType)
    {
        return lhsType.equals(PsiType.INT)
                || lhsType.equals(PsiType.SHORT)
                || lhsType.equals(PsiType.LONG)
                || lhsType.equals(PsiType.DOUBLE)
                || lhsType.equals(PsiType.FLOAT)
                || lhsType.equals(PsiType.CHAR)
                || lhsType.equals(PsiType.BOOLEAN)
                || lhsType.equals(PsiType.BYTE);
    }
}
