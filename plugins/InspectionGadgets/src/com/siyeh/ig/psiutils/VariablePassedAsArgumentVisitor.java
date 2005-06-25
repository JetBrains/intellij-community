package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class VariablePassedAsArgumentVisitor extends PsiRecursiveElementVisitor{
    @NotNull
    private final PsiVariable variable;
    private boolean passed = false;

    public VariablePassedAsArgumentVisitor(@NotNull PsiVariable variable){
        super();
        this.variable = variable;
    }

    public void visitElement(@NotNull PsiElement element){
        if(!passed){
            super.visitElement(element);
        }
    }

    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call){
        if(passed){
            return;
        }
        super.visitMethodCallExpression(call);
        final PsiExpressionList argumentList = call.getArgumentList();
        if(argumentList == null){
            return;
        }

        final PsiExpression[] args = argumentList.getExpressions();
        if(args == null){
            return;
        }
        for(final PsiExpression arg : args){

            if(VariableAccessUtils.mayEvaluateToVariable(arg, variable)){
                passed = true;
            }
        }
    }

    public void visitNewExpression(@NotNull PsiNewExpression newExpression){
        if(passed){
            return;
        }
        super.visitNewExpression(newExpression);
        final PsiExpressionList argumentList = newExpression.getArgumentList();
        if(argumentList == null){
            return;
        }
        final PsiExpression[] args = argumentList.getExpressions();
        if(args == null){
            return;
        }
        for(final PsiExpression arg : args){
            if(VariableAccessUtils.mayEvaluateToVariable(arg, variable)){
                passed = true;
            }
        }
    }

    public boolean isPassed(){
        return passed;
    }
}