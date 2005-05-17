package com.siyeh.ipp.shift;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;

class MultiplyByPowerOfTwoPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(element instanceof PsiBinaryExpression){
            return binaryExpressionIsMultiplyByPowerOfTwo((PsiBinaryExpression) element);
        } else if(element instanceof PsiAssignmentExpression){
            return assignmentExpressionIsMultiplyByPowerOfTwo((PsiAssignmentExpression) element);
        } else{
            return false;
        }
    }

    private boolean assignmentExpressionIsMultiplyByPowerOfTwo(PsiAssignmentExpression expression){
        final PsiJavaToken sign = expression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if(!tokenType.equals(JavaTokenType.ASTERISKEQ) &&
                !tokenType.equals(JavaTokenType.DIVEQ)){
            return false;
        }
        final PsiExpression lhs = expression.getLExpression();
        if(lhs == null){
            return false;
        }
        final PsiType lhsType = lhs.getType();
        if(lhsType == null){
            return false;
        }
        if(!ShiftUtils.isIntegral(lhsType)){
            return false;
        }
        final PsiExpression rhs = expression.getRExpression();
        if(rhs == null){
            return false;
        }
        return ShiftUtils.isPowerOfTwo(rhs);
    }

    private boolean binaryExpressionIsMultiplyByPowerOfTwo(PsiBinaryExpression expression){
        final PsiJavaToken sign = expression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if(!tokenType.equals(JavaTokenType.ASTERISK) &&
                !tokenType.equals(JavaTokenType.DIV)){
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
        if(!ShiftUtils.isIntegral(lhsType)){
            return false;
        }
        final PsiExpression rhs = expression.getROperand();
        if(rhs == null){
            return false;
        }
        return ShiftUtils.isPowerOfTwo(rhs);
    }
}
