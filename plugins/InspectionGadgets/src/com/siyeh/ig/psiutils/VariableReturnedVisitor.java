package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class VariableReturnedVisitor extends PsiRecursiveElementVisitor {
    private boolean returned = false;
    private final @NotNull PsiVariable variable;

    public VariableReturnedVisitor(@NotNull PsiVariable variable) {
        super();
        this.variable = variable;
    }


    public void visitReturnStatement(@NotNull PsiReturnStatement returnStatement){
        if(returned){
            return;
        }
        super.visitReturnStatement(returnStatement);
        final PsiExpression returnValue = returnStatement.getReturnValue();
        if(VariableAccessUtils.mayEvaluateToVariable(returnValue, variable))
        {
            returned = true;
        }

    }

    public boolean isReturned() {
        return returned;
    }
}
