package com.siyeh.ig.psiutils;

import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;

public class WellFormednessUtils{
    private WellFormednessUtils(){
        super();
    }

    public static boolean isWellFormed(@NotNull PsiAssignmentExpression expression){

        final PsiExpression rhs = expression.getRExpression();
        return rhs != null;
    }
}
