package com.siyeh.ig.methodmetrics;

import com.intellij.psi.*;

class LoopCountVisitor extends PsiRecursiveElementVisitor {
    private int m_count = 0;


    public void visitForStatement(PsiForStatement psiForStatement) {
        super.visitForStatement(psiForStatement);
        m_count++;
    }

    public void visitForeachStatement(PsiForeachStatement psiForStatement) {
        super.visitForeachStatement(psiForStatement);
        m_count++;
    }

    public void visitWhileStatement(PsiWhileStatement psiWhileStatement) {
        super.visitWhileStatement(psiWhileStatement);
        m_count++;
    }

    public void visitDoWhileStatement(PsiDoWhileStatement psiDoWhileStatement) {
        super.visitDoWhileStatement(psiDoWhileStatement);
        m_count++;
    }

    public void visitAnonymousClass(PsiAnonymousClass aClass) {
        // no call to super, to keep it from drilling into anonymous classes
    }

    public int getCount() {
        return m_count;
    }
}
