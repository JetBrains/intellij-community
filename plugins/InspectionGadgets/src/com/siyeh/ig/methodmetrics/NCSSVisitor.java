package com.siyeh.ig.methodmetrics;

import com.intellij.psi.*;

class NCSSVisitor extends PsiRecursiveElementVisitor {
    private int m_statementCount = 0;

    public void visitAnonymousClass(PsiAnonymousClass aClass) {
        // to call to super, to keep this from drilling down
    }

    public void visitStatement(PsiStatement statement) {
        super.visitStatement(statement);
        if (statement instanceof PsiEmptyStatement ||
                statement instanceof PsiBlockStatement) {
            return;
        }
        m_statementCount++;
    }

    public int getStatementCount() {
        return m_statementCount;
    }
}
