package com.siyeh.ig.psiutils;

import com.intellij.psi.*;

public class VariableAssignedFromVisitor extends PsiRecursiveElementVisitor {
    private boolean assignedFrom = false;
    private final PsiVariable variable;

    public VariableAssignedFromVisitor(PsiVariable variable) {
        super();
        this.variable = variable;
    }

    public void visitAssignmentExpression(PsiAssignmentExpression assignment){
        super.visitAssignmentExpression(assignment);
        final PsiExpression arg = assignment.getRExpression();
        if(!(arg instanceof PsiReferenceExpression)){
            return;
        }
        final PsiElement referent = ((PsiReference) arg).resolve();
        if(referent == null){
            return;
        }
        if(referent.equals(variable)){
            assignedFrom = true;
        }
    }

    public void visitVariable(PsiVariable var){
        super.visitVariable(var);
        final PsiExpression arg = var.getInitializer();
        if(!(arg instanceof PsiReferenceExpression)){
            return;
        }
        final PsiElement referent = ((PsiReference) arg).resolve();
        if(referent == null){
            return;
        }
        if(referent.equals(variable)){
            assignedFrom = true;
        }
    }

    public boolean isAssignedFrom() {
        return assignedFrom;
    }
}
