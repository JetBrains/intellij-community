package com.siyeh.ig.methodmetrics;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;

class NegationCountVisitor extends PsiRecursiveElementVisitor {
    private int m_count = 0;

    public void visitBinaryExpression(PsiBinaryExpression expression) {
        super.visitBinaryExpression(expression);
        final PsiJavaToken sign = expression.getOperationSign();
        if (sign == null) {
            return;
        }
        final IElementType tokenType = sign.getTokenType();
        if (tokenType.equals(JavaTokenType.NE)) {
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
