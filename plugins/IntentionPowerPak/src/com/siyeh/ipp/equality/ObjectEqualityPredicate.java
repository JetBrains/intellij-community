package com.siyeh.ipp.equality;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ipp.base.PsiElementPredicate;

class ObjectEqualityPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiBinaryExpression)){
            return false;
        }
        final PsiBinaryExpression expression = (PsiBinaryExpression) element;
        final PsiJavaToken sign = expression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if(!tokenType.equals(JavaTokenType.NE) &&
                !tokenType.equals(JavaTokenType.EQEQ)){
            return false;
        }
        final PsiExpression lhs = expression.getLOperand();
        if(lhs == null){
            return false;
        }
        final PsiType lhsType = lhs.getType();
        if(lhsType == null){
            return false;
        }
        final PsiExpression rhs = expression.getROperand();
        if(rhs == null){
            return false;
        }
        final PsiType rhsType = rhs.getType();
        if(rhsType == null){
            return false;
        }
        return !TypeConversionUtil.isPrimitiveAndNotNull(lhsType) &&
                !TypeConversionUtil.isPrimitiveAndNotNull(rhsType);
    }

}
