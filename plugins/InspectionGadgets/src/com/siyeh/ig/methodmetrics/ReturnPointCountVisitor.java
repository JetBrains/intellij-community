package com.siyeh.ig.methodmetrics;

import com.intellij.psi.*;

class ReturnPointCountVisitor extends PsiRecursiveElementVisitor {
    private int m_count = 0;


    public void visitAnonymousClass(PsiAnonymousClass aClass) {
        // no call to super, to keep it from drilling into anonymous classes
    }

    public void visitReturnStatement(PsiReturnStatement statement) {
        super.visitReturnStatement(statement);
        m_count++;
    }

    public int getCount() {
        return m_count;
    }
}
