package com.siyeh.ig.junit;

import com.intellij.psi.*;

class CallToSuperTeardownVisitor extends PsiRecursiveElementVisitor {
    private boolean m_callToSuperTeardownFound = false;

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
        if (!"tearDown".equals(methodName)) {
            return;
        }
        m_callToSuperTeardownFound = true;
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

    public boolean isCallToSuperTeardownFound() {
        return m_callToSuperTeardownFound;
    }
}
