package com.siyeh.ig.bugs;

import com.intellij.psi.*;

class ParameterClassCheckVisitor
        extends PsiRecursiveElementVisitor{
    private final PsiParameter parameter;

    private boolean checked = false;

    ParameterClassCheckVisitor(PsiParameter parameter){
        super();
        this.parameter = parameter;
    }

    public void visitElement(PsiElement element){
        if(!checked){
            super.visitElement(element);
        }
    }

    public void visitMethodCallExpression(PsiMethodCallExpression expression){
        if(checked){
            return;
        }
        super.visitMethodCallExpression(expression);
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        if(methodExpression == null){
            return;
        }
        final String methodName = methodExpression.getReferenceName();
        if(!"getClass".equals(methodName)){
            return;
        }
        final PsiExpressionList argList = expression.getArgumentList();
        if(argList == null){
            return;
        }
        final PsiExpression[] args = argList.getExpressions();
        if(args == null || args.length != 0){
            return;
        }
        final PsiExpression qualifier =
                methodExpression.getQualifierExpression();

        if(isParameterReference(qualifier)){
            checked = true;
        }
    }

    public void visitInstanceOfExpression(PsiInstanceOfExpression expression){
        if(checked){
            return;
        }
        super.visitInstanceOfExpression(expression);
        final PsiExpression operand = expression.getOperand();
        if(isParameterReference(operand)){
            checked = true;
        }
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

    public boolean isChecked(){
        return checked;
    }
}
