package com.siyeh.ig.psiutils;

import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiAssignmentExpression;

public class WellFormednessUtils{
    private WellFormednessUtils(){
        super();
    }

    public static boolean isWellFormed(PsiBinaryExpression expression)
    {
        final PsiExpression lhs = expression.getLOperand();
        if(lhs == null)
        {
            return false;
        }
        final PsiExpression rhs = expression.getROperand();
        if(rhs == null)
        {
            return false;
        }
        final PsiJavaToken operationSign = expression.getOperationSign();
        if(operationSign == null)
        {
            return false;
        }
        return true;
    }

    public static boolean isWellFormed(PsiAssignmentExpression expression){
        final PsiExpression lhs = expression.getLExpression();
        if(lhs == null){
            return false;
        }
        final PsiExpression rhs = expression.getRExpression();
        if(rhs == null){
            return false;
        }
        final PsiJavaToken operationSign = expression.getOperationSign();
        if(operationSign == null){
            return false;
        }
        return true;
    }
}
