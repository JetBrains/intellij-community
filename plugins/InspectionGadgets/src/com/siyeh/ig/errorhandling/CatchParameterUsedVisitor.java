package com.siyeh.ig.errorhandling;

import com.intellij.psi.*;

public class CatchParameterUsedVisitor extends PsiRecursiveElementVisitor {
    private final PsiParameter m_parameter;
    private boolean m_used = false;

    public CatchParameterUsedVisitor(PsiParameter variable) {
        super();
        m_parameter = variable;
    }

    public void visitReferenceExpression(PsiReferenceExpression reference) {
        final PsiExpression qualifier = reference.getQualifierExpression();
        if (qualifier != null) {
            qualifier.accept(this);
        }
        final PsiReferenceParameterList typeParameters = reference.getParameterList();
        if (typeParameters != null) {
            typeParameters.accept(this);
        }
        final PsiElement element = reference.resolve();
        if (m_parameter.equals(element)) {
            m_used = true;
        }
    }

    public boolean isUsed() {
        return m_used;
    }
}
