package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class VariableUsedInArrayInitializerVisitor extends PsiRecursiveElementVisitor{
    @NotNull
    private final PsiVariable variable;
    private boolean passed = false;

    public VariableUsedInArrayInitializerVisitor(@NotNull PsiVariable variable){
        super();
        this.variable = variable;
    }

    public void visitElement(@NotNull PsiElement element){
        if(!passed){
            super.visitElement(element);
        }
    }

    public void visitArrayInitializerExpression(
            PsiArrayInitializerExpression expression){
        if(passed){
            return;
        }
        super.visitArrayInitializerExpression(expression);

        final PsiExpression[] args = expression.getInitializers();
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