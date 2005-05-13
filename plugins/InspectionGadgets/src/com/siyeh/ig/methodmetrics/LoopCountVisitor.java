package com.siyeh.ig.methodmetrics;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

class LoopCountVisitor extends PsiRecursiveElementVisitor {
    private int m_count = 0;


    public void visitForStatement(@NotNull PsiForStatement psiForStatement) {
        super.visitForStatement(psiForStatement);
        m_count++;
    }

    public void visitForeachStatement(@NotNull PsiForeachStatement psiForStatement) {
        super.visitForeachStatement(psiForStatement);
        m_count++;
    }

    public void visitWhileStatement(@NotNull PsiWhileStatement psiWhileStatement) {
        super.visitWhileStatement(psiWhileStatement);
        m_count++;
    }

    public void visitDoWhileStatement(@NotNull PsiDoWhileStatement psiDoWhileStatement) {
        super.visitDoWhileStatement(psiDoWhileStatement);
        m_count++;
    }

    public void visitAnonymousClass(@NotNull PsiAnonymousClass aClass) {
        // no call to super, to keep it from drilling into anonymous classes
    }

    public int getCount() {
        return m_count;
    }
}
