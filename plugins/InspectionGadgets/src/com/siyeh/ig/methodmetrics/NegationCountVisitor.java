package com.siyeh.ig.methodmetrics;

import com.intellij.psi.*;

class NegationCountVisitor extends PsiRecursiveElementVisitor {
    private int m_count = 0;

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

    public void visitBinaryExpression(PsiBinaryExpression expression) {
        super.visitBinaryExpression(expression);
        final PsiJavaToken sign = expression.getOperationSign();
        if (sign == null) {
            return;
        }
        if (!(sign.getTokenType() != JavaTokenType.NE)) {
            m_count++;
        }
    }

    public void visitAnonymousClass(PsiAnonymousClass aClass) {
        // no call to super, to keep it from drilling into anonymous classes
    }

    public void visitPrefixExpression(PsiPrefixExpression expression) {
        super.visitPrefixExpression(expression);
        final PsiJavaToken sign = expression.getOperationSign();
        if (sign == null) {
            return;
        }
        if (sign.getTokenType().equals(JavaTokenType.EXCL)) {
            m_count++;
        }
    }

    public int getCount() {
        return m_count;
    }
}
