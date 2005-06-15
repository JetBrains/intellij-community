package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class VariableAssignedFromVisitor extends PsiRecursiveElementVisitor{
    private boolean assignedFrom = false;
    private final @NotNull PsiVariable variable;

    public VariableAssignedFromVisitor(@NotNull PsiVariable variable){
        super();
        this.variable = variable;
    }

    public void visitElement(@NotNull PsiElement element){
        if(!assignedFrom){
            super.visitElement(element);
        }
    }

    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression assignment){
        if(assignedFrom){
            return;
        }
        super.visitAssignmentExpression(assignment);
        final PsiExpression arg = assignment.getRExpression();
        if(VariableAccessUtils.mayEvaluateToVariable(arg, variable)){
            assignedFrom = true;
        }
    }

    public void visitVariable(@NotNull PsiVariable var){
        if(assignedFrom){
            return;
        }
        super.visitVariable(var);
        final PsiExpression arg = var.getInitializer();
        if(VariableAccessUtils.mayEvaluateToVariable(arg, variable)){
            assignedFrom = true;
        }
    }

    public boolean isAssignedFrom(){
        return assignedFrom;
    }
}
