package com.siyeh.ipp.opassign;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.EquivalenceChecker;
import com.siyeh.ipp.psiutils.SideEffectChecker;

class AssignmentExpressionReplaceableWithOperatorAssigment implements PsiElementPredicate
{
    public boolean satisfiedBy(PsiElement element)
    {
        if(!(element instanceof PsiAssignmentExpression))
        {
            return false;
        }
        final PsiAssignmentExpression assignment = (PsiAssignmentExpression) element;
        final PsiJavaToken sign = assignment.getOperationSign();
        if(sign.getTokenType() != JavaTokenType.EQ)
        {
            return false;
        }
        final PsiExpression lhs = assignment.getLExpression();
        final PsiExpression rhs = assignment.getRExpression();
        if(lhs == null || rhs == null)
        {
            return false;
        }
        if(!(rhs instanceof PsiBinaryExpression))
        {
            return false;
        }
        final PsiBinaryExpression binaryRhs = (PsiBinaryExpression) rhs;
        final PsiJavaToken operatorSign = binaryRhs.getOperationSign();
        if(operatorSign.getTokenType() == JavaTokenType.OROR ||
                operatorSign.getTokenType() == JavaTokenType.ANDAND)
        {
            return false;
        }
        if(SideEffectChecker.mayHaveSideEffects(lhs))
        {
            return false;
        }
        final PsiExpression rhsLhs = binaryRhs.getLOperand();
        return EquivalenceChecker.expressionsAreEquivalent(lhs, rhsLhs);
    }
}
