package com.siyeh.ig.psiutils;

import com.intellij.psi.*;

public class VariableUsedInInnerClassVisitor extends PsiRecursiveElementVisitor {
    private final PsiVariable m_variable;
    private boolean m_usedInInnerClass = false;
    private boolean m_inInnerClass = false;

    public VariableUsedInInnerClassVisitor(PsiVariable variable) {
        super();
        m_variable = variable;
    }

    public void visitAnonymousClass(PsiAnonymousClass psiAnonymousClass) {
        final boolean wasInInnerClass = m_inInnerClass;
        m_inInnerClass = true;
        super.visitAnonymousClass(psiAnonymousClass);
        m_inInnerClass = wasInInnerClass;
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
        if (!m_inInnerClass) {
            return;
        }
        final PsiElement element = ref.resolve();
        if (m_variable.equals(element)) {
            m_usedInInnerClass = true;
        }
    }

    public boolean isUsedInInnerClass() {
        return m_usedInInnerClass;
    }
}
