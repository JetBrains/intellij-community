package com.siyeh.ig.confusing;

import com.intellij.psi.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

class LocalVariableAccessVisitor extends PsiRecursiveElementVisitor {
    private final Set m_accesssedVariables = new HashSet(2);

    LocalVariableAccessVisitor() {
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
        if (qualifier != null && !(qualifier instanceof PsiThisExpression)) {
            return;
        }
        final PsiElement element = ref.resolve();
        if (!(element instanceof PsiLocalVariable)) {
            return;
        }
        m_accesssedVariables.add(element);
    }

    public Set getAccessedVariables() {
        return Collections.unmodifiableSet(m_accesssedVariables);
    }
}
