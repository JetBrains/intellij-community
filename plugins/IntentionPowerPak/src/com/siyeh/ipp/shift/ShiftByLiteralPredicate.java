package com.siyeh.ipp.shift;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.base.PsiElementPredicate;

class ShiftByLiteralPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(element instanceof PsiBinaryExpression){
            return isBinaryShiftByLiteral((PsiBinaryExpression) element);
        }
        if(element instanceof PsiAssignmentExpression){
            return isAssignmentShiftByLiteral((PsiAssignmentExpression) element);
        } else{
            return false;
        }
    }

    private boolean isAssignmentShiftByLiteral(PsiAssignmentExpression expression){
        final PsiJavaToken sign = expression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if(!tokenType.equals(JavaTokenType.LTLTEQ) &&
                !tokenType.equals(JavaTokenType.GTGTEQ)){
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
        return ShiftUtils.isIntLiteral(rhs);
    }

    private boolean isBinaryShiftByLiteral(PsiBinaryExpression expression){
        final PsiJavaToken sign = expression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if(!tokenType.equals(JavaTokenType.LTLT) &&
                !tokenType.equals(JavaTokenType.GTGT)){
            return false;
        }
        final PsiExpression lOperand = expression.getLOperand();
        final PsiType lhsType = lOperand.getType();
        if(!ShiftUtils.isIntegral(lhsType)){
            return false;
        }
        final PsiExpression rhs = expression.getROperand();
        return ShiftUtils.isIntLiteral(rhs);
    }
}
