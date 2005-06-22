package com.siyeh.ig.numeric;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.ComparisonUtils;
import org.jetbrains.annotations.NotNull;

public class ComparisonOfShortAndCharInspection extends ExpressionInspection{

    public String getDisplayName(){
        return "Comparison of 'short' and 'char' values";
    }

    public String getGroupDisplayName(){
        return GroupNames.NUMERIC_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Equality comparison (#ref) of short and char values #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ComparisonOfShortAndCharVisitor();
    }

    private static class ComparisonOfShortAndCharVisitor
            extends BaseInspectionVisitor{


        public void visitBinaryExpression(@NotNull PsiBinaryExpression expression){
            super.visitBinaryExpression(expression);
            if(!(expression.getROperand() != null)){
                return;
            }
            if(!ComparisonUtils.isEqualityComparison(expression)){
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            final PsiType lhsType = lhs.getType();
            final PsiExpression rhs = expression.getROperand();
            if(rhs == null)
            {
                return;
            }
            final PsiType rhsType = rhs.getType();
            if(PsiType.SHORT.equals(lhsType)&&
                       PsiType.CHAR.equals(rhsType)){
                registerError(expression);
            } else if(PsiType.CHAR.equals(lhsType) &&
                              PsiType.SHORT.equals(rhsType)){
                registerError(expression);
            }
        }
    }

}
