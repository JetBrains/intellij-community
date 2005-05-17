package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

public class InstanceofIncompatibleInterfaceInspection
        extends ExpressionInspection{
    public String getDisplayName(){
        return "'instanceof' with incompatible interface";
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "'instanceof' incompatible interface #ref #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new InstanceofIncompatibleInterfaceVisitor();
    }

    private static class InstanceofIncompatibleInterfaceVisitor
            extends BaseInspectionVisitor{


        public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression expression){
            super.visitInstanceOfExpression(expression);

            final PsiTypeElement castTypeElement = expression.getCheckType();
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
