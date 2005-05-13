package com.siyeh.ig.psiutils;

import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import org.jetbrains.annotations.NotNull;

public class WellFormednessUtils{
    private WellFormednessUtils(){
        super();
    }

    public static boolean isWellFormed(@NotNull PsiBinaryExpression expression)
    {
        final PsiExpression lhs = expression.getLOperand();
        if(lhs == null )
        {
            return false;
        }
        final PsiExpression rhs = expression.getROperand();
        if(rhs == null )
        {
            return false;
        }
        final PsiJavaToken operationSign = expression.getOperationSign();
        return operationSign != null;
    }

    public static boolean isWellFormed(@NotNull PsiAssignmentExpression expression){
        final PsiExpression lhs = expression.getLExpression();
        if(lhs == null || !lhs.isValid()){
            return false;
        }
        final PsiExpression rhs = expression.getRExpression();
        if(rhs == null || !rhs.isValid()){
            return false;
        }
        final PsiJavaToken operationSign = expression.getOperationSign();
        return operationSign != null;
    }
}
