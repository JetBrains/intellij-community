package com.siyeh.ig.finalization;

import com.intellij.psi.*;

class CallToSuperFinalizeVisitor extends PsiRecursiveElementVisitor {
    private boolean m_callToSuperFinalizeFound = false;

    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        if (methodExpression == null) {
            return;
        }
        final PsiExpression target = methodExpression.getQualifierExpression();
        if (target == null) {
            return;
        }
        if (!(target instanceof PsiSuperExpression)) {
            return;
        }
        final String methodName = methodExpression.getReferenceName();
        if (!"finalize".equals(methodName)) {
            return;
        }
        m_callToSuperFinalizeFound = true;
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
        final PsiExpression qualifier = expression.getQualifierExpression();
        if (qualifier != null) {
            qualifier.accept(this);
        }
        final PsiReferenceParameterList typeParameters = expression.getParameterList();
        if (typeParameters != null) {
            typeParameters.accept(this);
        }
    }

    public boolean isCallToSuperFinalizeFound() {
        return m_callToSuperFinalizeFound;
    }
}
