package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.WellFormednessUtils;

public class ComparisonOfShortAndCharInspection extends ExpressionInspection{

    public String getDisplayName(){
        return "Comparison of 'short' and 'char' values";
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Equality comparison (#ref) of short and char values #loc";
    }

    public BaseInspectionVisitor createVisitor(
            InspectionManager inspectionManager, boolean onTheFly){
        return new ComparisonOfShortAndCharVisitor(this, inspectionManager,
                                                   onTheFly);
    }

    private static class ComparisonOfShortAndCharVisitor
            extends BaseInspectionVisitor{

        private ComparisonOfShortAndCharVisitor(BaseInspection inspection,
                                                InspectionManager inspectionManager,
                                                boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitBinaryExpression(PsiBinaryExpression expression){
            super.visitBinaryExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }
            if(!ComparisonUtils.isEqualityComparison(expression)){
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            final PsiType lhsType = lhs.getType();
            final PsiExpression rhs = expression.getROperand();
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
