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

    public boolean isCallToSuperTeardownFound() {
        return m_callToSuperTeardownFound;
    }
}
