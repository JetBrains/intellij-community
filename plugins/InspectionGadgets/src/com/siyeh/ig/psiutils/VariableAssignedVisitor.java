package com.siyeh.ig.psiutils;

import com.intellij.psi.*;

public class VariableAssignedVisitor extends PsiRecursiveElementVisitor {
    private boolean assigned = false;
    private final PsiVariable variable;

    public VariableAssignedVisitor(PsiVariable variable) {
        super();
        this.variable = variable;
    }

    public void visitReferenceExpression(PsiReferenceExpression ref) {
        final PsiExpression qualifier = ref.getQualifierExpression();
        if (qualifier != null) {
            qualifier.accept(this);
        }
        final PsiReferenceParameterList typeParameters = ref.getParameterList();
        if (typeParameters != null) {
            typeParameters.accept(this);
        }
    }

    public void visitAssignmentExpression(PsiAssignmentExpression assignment) {
        super.visitAssignmentExpression(assignment);
        final PsiExpression arg = assignment.getLExpression();
        if (!(arg instanceof PsiReferenceExpression)) {
            return;
        }
        final PsiElement referent = ((PsiReference) arg).resolve();
        if (referent == null) {
            return;
        }
        if (referent.equals(variable)) {
            assigned = true;
        }
    }

    public boolean isAssigned() {
        return assigned;
    }
}
