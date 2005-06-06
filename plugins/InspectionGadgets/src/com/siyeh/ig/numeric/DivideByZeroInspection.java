package com.siyeh.ig.numeric;

import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

public class DivideByZeroInspection extends ExpressionInspection {
    public String getID(){
        return "divzero";
    }

    public String getDisplayName() {
        return "Division by zero";
    }

    public String getGroupDisplayName() {
        return GroupNames.NUMERIC_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Division by zero #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new DivisionByZeroVisitor();
    }

    private static class DivisionByZeroVisitor extends BaseInspectionVisitor {

        public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            final PsiExpression rhs = expression.getROperand();
            if(rhs == null){
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.DIV)  && !tokenType.equals(JavaTokenType.PERC)) {
                return;
            }
            final Object value = ConstantExpressionUtil.computeCastTo(rhs, PsiType.DOUBLE);
            if(value == null || !(value instanceof Double))
            {
                return;
            }
            final double constantValue = (Double) value;
            if(constantValue == 0.0 || constantValue == -0.0)
            {
                registerError(expression);
            }
        }

        public void visitAssignmentExpression(PsiAssignmentExpression expression){
            super.visitAssignmentExpression(expression);
            final PsiExpression rhs = expression.getRExpression();
            if(rhs == null){
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if(!tokenType.equals(JavaTokenType.DIVEQ)
                    && !tokenType.equals(JavaTokenType.PERCEQ)){
                return;
            }
            final Object value = ConstantExpressionUtil.computeCastTo(rhs,
                                                                      PsiType.DOUBLE);
            if(value == null || !(value instanceof Double)){
                return;
            }
            final double constantValue = (Double) value;
            if(constantValue == 0.0 || constantValue == -0.0){
                registerError(expression);
            }
        }
    }

}
