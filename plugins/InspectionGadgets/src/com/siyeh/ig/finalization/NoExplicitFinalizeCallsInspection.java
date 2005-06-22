package com.siyeh.ig.finalization;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import org.jetbrains.annotations.NotNull;

public class NoExplicitFinalizeCallsInspection extends ExpressionInspection{
    public String getID(){
        return "FinalizeCalledExplicitly";
    }

    public String getDisplayName(){
        return "'finalize()' called explicitly";
    }

    public String getGroupDisplayName(){
        return GroupNames.FINALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "#ref() called explicitly #loc";
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new NoExplicitFinalizeCallsVisitor();
    }

    private static class NoExplicitFinalizeCallsVisitor
            extends BaseInspectionVisitor{
        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression
                    .getMethodExpression();
            if(methodExpression == null){
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if(!"finalize".equals(methodName)){
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            if(argumentList == null){
                return;
            }
            if(argumentList.getExpressions().length != 0){
                return;
            }
            final PsiMethod containingMethod =
                    PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
            if(containingMethod == null){
                return;
            }
            final String containingMethodName = containingMethod.getName();
            final PsiParameterList parameterList = containingMethod
                    .getParameterList();
            if("finalize".equals(containingMethodName)
                    && parameterList.getParameters().length == 0){
                return;
            }
            registerMethodCallError(expression);
        }
    }
}
