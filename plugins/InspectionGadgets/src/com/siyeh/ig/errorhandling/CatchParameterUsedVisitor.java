package com.siyeh.ig.errorhandling;

import com.intellij.psi.*;

class CatchParameterUsedVisitor extends PsiRecursiveElementVisitor {
    private final PsiParameter m_parameter;
    private boolean m_used = false;

    public CatchParameterUsedVisitor(PsiParameter variable) {
        super();
        m_parameter = variable;
    }

    public void visitReferenceExpression(PsiReferenceExpression reference) {
        super.visitReferenceExpression(reference);
        final PsiElement element = reference.resolve();
        if (m_parameter.equals(element)) {
            m_used = true;
        }
    }

    public boolean isUsed() {
        return m_used;
    }
}
