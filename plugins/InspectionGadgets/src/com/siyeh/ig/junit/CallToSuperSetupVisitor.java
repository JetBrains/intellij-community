package com.siyeh.ig.junit;

import com.intellij.psi.*;

class CallToSuperSetupVisitor extends PsiRecursiveElementVisitor {
    private boolean m_callToSuperSetupFound = false;

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
        if (!"setUp".equals(methodName)) {
            return;
        }
        m_callToSuperSetupFound = true;
    }

    public boolean isCallToSuperSetupFound() {
        return m_callToSuperSetupFound;
    }
}
