package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;

public class SideEffectChecker {
    private SideEffectChecker() {
        super();

    }

    public static boolean mayHaveSideEffects(PsiExpression exp) {
        final SideEffectsVisitor visitor = new SideEffectsVisitor();
        exp.accept(visitor);
        return visitor.mayHaveSideEffects();
    }

    private static class SideEffectsVisitor extends PsiRecursiveElementVisitor {
        private boolean m_mayHaveSideEffects = false;

        private boolean mayHaveSideEffects() {
            return m_mayHaveSideEffects;
        }

        public void visitReferenceExpression(PsiReferenceExpression expression) {
            final PsiExpression qualifier = expression.getQualifierExpression();
            if (qualifier != null) {
                qualifier.accept(this);
            }
            final PsiReferenceParameterList typeParameters = expression.getParameterList();
            if (typeParameters != null) {
                typeParameters.accept(this);
            }
        }

        public void visitAssignmentExpression(PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);
            m_mayHaveSideEffects = true;
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            m_mayHaveSideEffects = true;
        }

        public void visitNewExpression(PsiNewExpression expression) {
            super.visitNewExpression(expression);
            m_mayHaveSideEffects = true;
        }

        public void visitPostfixExpression(PsiPostfixExpression expression) {
            super.visitPostfixExpression(expression);
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (tokenType.equals(JavaTokenType.PLUSPLUS) ||
                    tokenType.equals(JavaTokenType.MINUSMINUS)) {
                m_mayHaveSideEffects = true;
            }
        }

        public void visitPrefixExpression(PsiPrefixExpression expression) {
            super.visitPrefixExpression(expression);
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (tokenType.equals(JavaTokenType.PLUSPLUS) ||
                    tokenType.equals(JavaTokenType.MINUSMINUS)) {
                m_mayHaveSideEffects = true;
            }
        }
    }
}
