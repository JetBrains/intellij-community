package com.siyeh.ig.performance;

import com.intellij.psi.*;

class CanBeStaticVisitor extends PsiRecursiveElementVisitor {
    private boolean m_canBeStatic = true;

    CanBeStaticVisitor() {
        super();
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
        final PsiElement element = ref.resolve();
        if (element instanceof PsiField) {
            final PsiField field = (PsiField) element;
            if (!field.hasModifierProperty(PsiModifier.STATIC)) {
                m_canBeStatic = false;
            }
        }
    }

    public boolean canBeStatic() {
        return m_canBeStatic;
    }
}
