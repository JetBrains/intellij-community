package com.siyeh.ig.methodmetrics;

import com.intellij.psi.*;

class NestingDepthVisitor extends PsiRecursiveElementVisitor {
    private int m_maximumDepth = 0;
    private int m_currentDepth = 0;

    public void visitReferenceExpression(PsiReferenceExpression ref) {
        final PsiExpression qualifier = ref.getQualifierExpression();
        if (qualifier != null) {
            qualifier.accept(this);
        }
        final PsiReferenceParameterList typeParameters = ref.getParameterList();
        if (typeParameters != null) {
            typeParameters.accept(this);
        }    }

    public void visitAnonymousClass(PsiAnonymousClass aClass) {
        // to call to super, to keep this from drilling down
    }

    public void visitBlockStatement(PsiBlockStatement statement) {
        final PsiElement parent = statement.getParent();
        final boolean isAlreadyCounted = parent instanceof PsiDoWhileStatement ||
                parent instanceof PsiWhileStatement ||
                parent instanceof PsiForStatement ||
                parent instanceof PsiIfStatement ||
                parent instanceof PsiSynchronizedStatement ||
                parent instanceof PsiTryStatement;
        if (!isAlreadyCounted) {
            enterScope();
        }
        super.visitBlockStatement(statement);
        if (!isAlreadyCounted) {
            exitScope();
        }
    }

    public void visitDoWhileStatement(PsiDoWhileStatement statement) {
        enterScope();
        super.visitDoWhileStatement(statement);
        exitScope();
    }

    public void visitForStatement(PsiForStatement statement) {
        enterScope();
        super.visitForStatement(statement);
        exitScope();
    }

    public void visitIfStatement(PsiIfStatement statement) {
        boolean isAlreadyCounted = false;
        if (statement.getParent() instanceof PsiIfStatement) {
            final PsiIfStatement parent = (PsiIfStatement) statement.getParent();
            final PsiStatement elseBranch = parent.getElseBranch();
            if (statement.equals(elseBranch)) {
                isAlreadyCounted = true;
            }
        }
        if (!isAlreadyCounted) {
            enterScope();
        }
        super.visitIfStatement(statement);
        if (!isAlreadyCounted) {
            exitScope();
        }
    }

    public void visitSynchronizedStatement(PsiSynchronizedStatement statement) {
        enterScope();
        super.visitSynchronizedStatement(statement);
        exitScope();
    }

    public void visitTryStatement(PsiTryStatement statement) {
        enterScope();
        super.visitTryStatement(statement);
        exitScope();
    }

    public void visitSwitchStatement(PsiSwitchStatement statement) {
        enterScope();
        super.visitSwitchStatement(statement);
        exitScope();
    }

    public void visitWhileStatement(PsiWhileStatement statement) {
        enterScope();
        super.visitWhileStatement(statement);
        exitScope();
    }

    private void enterScope() {
        m_currentDepth++;
        m_maximumDepth = Math.max(m_maximumDepth, m_currentDepth);
    }

    private void exitScope() {
        m_currentDepth--;
    }

    public int getMaximumDepth() {
        return m_maximumDepth;
    }
}
