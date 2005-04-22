package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.WellFormednessUtils;

public class ShiftOutOfRangeInspection extends ExpressionInspection{
    public String getDisplayName(){
        return "Shift operation by inappropriate constant";
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        final PsiBinaryExpression binaryExp = (PsiBinaryExpression) location.getParent();
        final PsiExpression rhs = binaryExp.getROperand();
        final Integer value = (Integer) ConstantExpressionUtil.computeCastTo(rhs,
                                                                  PsiType.INT);
        if(value.intValue()>0){
            return "Shift operation #ref by overly large constant value #loc";
        } else{
            return "Shift operation #ref by negative constant value #loc";
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new ShiftOutOfRange(this, inspectionManager, onTheFly);
    }

    private static class ShiftOutOfRange extends BaseInspectionVisitor{
        private ShiftOutOfRange(BaseInspection inspection,
                                InspectionManager inspectionManager,
                                boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitBinaryExpression(PsiBinaryExpression expression){
            super.visitBinaryExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression)){
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
            final int value = valueObject.intValue();
            if(expressionType.equals(PsiType.LONG)){
                if(value < 0 || value>63)
                {
                    registerError(sign);
                }
            }
            if(expressionType.equals(PsiType.INT)){
                if(value < 0 || value > 31){
                    registerError(sign);
                }
            }
        }
    }

}
