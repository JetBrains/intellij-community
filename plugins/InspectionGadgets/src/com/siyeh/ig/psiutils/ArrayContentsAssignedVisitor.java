package com.siyeh.ig.psiutils;

import com.intellij.psi.*;

public class ArrayContentsAssignedVisitor extends PsiRecursiveElementVisitor {
    private boolean assigned = false;
    private final PsiVariable variable;

    public ArrayContentsAssignedVisitor(PsiVariable variable) {
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
        final PsiJavaToken operationSign = assignment.getOperationSign();
        if (operationSign.getTokenType() != JavaTokenType.EQ) {
            return;
        }
        final PsiExpression arg = assignment.getLExpression();
        if (arg == null) {
            return;
        }
        if (!(arg instanceof PsiArrayAccessExpression)) {
            return;
        }
        final PsiExpression arrayExpression = ((PsiArrayAccessExpression) arg).getArrayExpression();
        if (arrayExpression == null) {
            return;
        }
        if (!(arrayExpression instanceof PsiReferenceExpression)) {
            return;
        }
        final PsiElement referent = ((PsiReference) arrayExpression).resolve();
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
