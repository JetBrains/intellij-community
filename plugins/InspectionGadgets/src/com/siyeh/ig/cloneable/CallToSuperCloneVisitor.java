package com.siyeh.ig.cloneable;

import com.intellij.psi.*;

class CallToSuperCloneVisitor extends PsiRecursiveElementVisitor {
    private boolean m_callToSuperCloneFound = false;

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
        if (!"clone".equals(methodName)) {
            return;
        }
        m_callToSuperCloneFound = true;
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

    public boolean isCallToSuperCloneFound() {
        return m_callToSuperCloneFound;
    }
}
