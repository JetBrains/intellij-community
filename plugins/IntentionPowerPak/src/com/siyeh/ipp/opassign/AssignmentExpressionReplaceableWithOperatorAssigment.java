package com.siyeh.ipp.opassign;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.EquivalenceChecker;
import com.siyeh.ipp.psiutils.SideEffectChecker;

class AssignmentExpressionReplaceableWithOperatorAssigment
        implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiAssignmentExpression)){
            return false;
        }
        final PsiAssignmentExpression assignment =
                (PsiAssignmentExpression) element;
        final PsiJavaToken sign = assignment.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if(!JavaTokenType.EQ.equals(tokenType)){
            return false;
        }
        final PsiExpression lhs = assignment.getLExpression();
        final PsiExpression rhs = assignment.getRExpression();
        if(lhs == null || rhs == null){
            return false;
        }
        if(!(rhs instanceof PsiBinaryExpression)){
            return false;
        }
        final PsiBinaryExpression binaryRhs = (PsiBinaryExpression) rhs;
        final PsiJavaToken operatorSign = binaryRhs.getOperationSign();
        final IElementType rhsTokenType = operatorSign.getTokenType();
        if(JavaTokenType.OROR.equals(rhsTokenType) ||
                   JavaTokenType.ANDAND.equals(rhsTokenType)){
            return false;
        }
        if(SideEffectChecker.mayHaveSideEffects(lhs)){
            return false;
        }
        final PsiExpression rhsLhs = binaryRhs.getLOperand();
        return EquivalenceChecker.expressionsAreEquivalent(lhs, rhsLhs);
    }
}
