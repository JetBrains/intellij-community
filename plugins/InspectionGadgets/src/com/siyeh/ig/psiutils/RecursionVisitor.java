package com.siyeh.ig.psiutils;

import com.intellij.psi.*;

public class RecursionVisitor extends PsiRecursiveElementVisitor {
    private boolean m_recursive = false;
    private final PsiMethod m_method;

    public RecursionVisitor(PsiMethod method) {
        super();
        m_method = method;
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

    public void visitMethodCallExpression(PsiMethodCallExpression psiMethodCallExpression) {
        super.visitMethodCallExpression(psiMethodCallExpression);
        final PsiMethod method = psiMethodCallExpression.resolveMethod();
        if (method != null) {
            if (method.equals(m_method)) {
                m_recursive = true;
            }
        }
    }

    public boolean isRecursive() {
        return m_recursive;
    }
}
