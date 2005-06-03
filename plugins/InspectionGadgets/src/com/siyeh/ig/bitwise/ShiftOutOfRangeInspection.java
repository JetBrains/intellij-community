package com.siyeh.ig.bitwise;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

public class ShiftOutOfRangeInspection extends ExpressionInspection{
    public String getDisplayName(){
        return "Shift operation by inappropriate constant";
    }

    public String getGroupDisplayName(){
        return GroupNames.BITWISE_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        final PsiBinaryExpression binaryExp = (PsiBinaryExpression) location.getParent();
        final PsiExpression rhs = binaryExp.getROperand();
        final Integer value = (Integer) ConstantExpressionUtil.computeCastTo(rhs,
                                                                  PsiType.INT);
        if(value>0){
            return "Shift operation #ref by overly large constant value #loc";
        } else{
            return "Shift operation #ref by negative constant value #loc";
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ShiftOutOfRange();
    }

    private static class ShiftOutOfRange extends BaseInspectionVisitor{

        public void visitBinaryExpression(@NotNull PsiBinaryExpression expression){
            super.visitBinaryExpression(expression);
            if(!(expression.getROperand() != null)){
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if(!tokenType.equals(JavaTokenType.LTLT) &&
                       !tokenType.equals(JavaTokenType.GTGT) &&
                       !tokenType.equals(JavaTokenType.GTGTGT)){
                return;
            }

            final PsiType expressionType = expression.getType();
            if(expressionType == null){
                return;
            }
            final PsiExpression rhs = expression.getROperand();
            if(!PsiUtil.isConstantExpression(rhs)){
                return;
            }
            final Integer valueObject =
                    (Integer) ConstantExpressionUtil.computeCastTo(rhs,
                                                                   PsiType.INT);
            if(valueObject == null)
            {
                return;
            }
            if(expressionType.equals(PsiType.LONG)){
                if(valueObject < 0 || valueObject>63)
                {
                    registerError(sign);
                }
            }
            if(expressionType.equals(PsiType.INT)){
                if(valueObject < 0 || valueObject > 31){
                    registerError(sign);
                }
            }
        }
    }

}
