package com.siyeh.ig.bugs;

import com.intellij.psi.*;

public class ParameterClassCheckVisitor
        extends PsiRecursiveElementVisitor{
    private final PsiParameter parameter;

    public boolean isChecked(){
        return checked;
    }

    private boolean checked = false;

    public ParameterClassCheckVisitor(PsiParameter parameter){
        super();
        this.parameter = parameter;
    }

    public void visitMethodCallExpression(PsiMethodCallExpression expression){
        super.visitMethodCallExpression(expression);
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        if(methodExpression == null)
        {
            return;
        }
        final String methodName = methodExpression.getReferenceName();
        if(!"getClass".equals(methodName))
        {
            return;
        }
        final PsiExpressionList argList = expression.getArgumentList();
        if(argList == null)
        {
            return;
        }
        final PsiExpression[] args = argList.getExpressions();
        if(args == null || args.length!=0)
        {
            return;
        }
        final PsiExpression qualifier = methodExpression.getQualifierExpression();

        if(!isParameterReference(qualifier)){
            return;
        }
        checked = true;
    }

    public void visitInstanceOfExpression(PsiInstanceOfExpression expression){
        super.visitInstanceOfExpression(expression);
        final PsiExpression operand = expression.getOperand();
        if(!isParameterReference(operand))
        {
            return;
        }
        checked = true;
    }

    private boolean isParameterReference(PsiExpression operand){
        if(operand == null){
            return false;
        }
        if(!(operand instanceof PsiReferenceExpression)){
            return false;
        }
        final PsiElement referent = ((PsiReference) operand).resolve();
        if(referent == null){
            return false;
        }
        return referent.equals(parameter);
    }
}
