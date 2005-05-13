package com.siyeh.ig.methodmetrics;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

class NegationCountVisitor extends PsiRecursiveElementVisitor {
    private int m_count = 0;

    public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
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

    public void visitAnonymousClass(@NotNull PsiAnonymousClass aClass) {
        // no call to super, to keep it from drilling into anonymous classes
    }

    public void visitPrefixExpression(@NotNull PsiPrefixExpression expression) {
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
