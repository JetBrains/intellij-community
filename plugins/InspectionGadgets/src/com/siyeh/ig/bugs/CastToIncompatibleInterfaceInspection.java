package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import org.jetbrains.annotations.NotNull;

public class CastToIncompatibleInterfaceInspection extends ExpressionInspection{
    public String getDisplayName(){
        return "Casting to incompatible interface";
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Cast to incompatible interface #ref #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new CastToIncompatibleInterfaceVisitor();
    }

    private static class CastToIncompatibleInterfaceVisitor
            extends BaseInspectionVisitor{

        public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression){
            super.visitTypeCastExpression(expression);

            final PsiTypeElement castTypeElement = expression.getCastType();
            if(castTypeElement == null){
                return;
            }
            final PsiType castType = castTypeElement.getType();
            if(!(castType instanceof PsiClassType)){
                return;
            }
            final PsiClass castClass = ((PsiClassType) castType).resolve();
            if(castClass == null){
                return;
            }
            if(!castClass.isInterface()){
                return;
            }
            final PsiExpression operand = expression.getOperand();
            if(operand == null){
                return;
            }
            final PsiType operandType = operand.getType();
            if(!(operandType instanceof PsiClassType)){
                return;
            }
            final PsiClass operandClass =
                    ((PsiClassType) operandType).resolve();
            if(operandClass == null){
                return;
            }
            if(InheritanceUtil.existsMutualSubclass(operandClass, castClass)){
                return;
            }
            registerError(castTypeElement);
        }
    }

}
