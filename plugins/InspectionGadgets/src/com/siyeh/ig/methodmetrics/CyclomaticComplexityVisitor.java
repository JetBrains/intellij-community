package com.siyeh.ig.methodmetrics;

import com.intellij.psi.*;

class CyclomaticComplexityVisitor extends PsiRecursiveElementVisitor {
    private int m_complexity = 1;

    public void visitAnonymousClass(PsiAnonymousClass aClass) {
        // to call to super, to keep this from drilling down
    }

    public void visitForStatement(PsiForStatement statement) {
        super.visitForStatement(statement);
        m_complexity++;
    }

    public void visitForeachStatement(PsiForeachStatement statement) {
        super.visitForeachStatement(statement);
        m_complexity++;
    }

    public void visitIfStatement(PsiIfStatement statement) {
        super.visitIfStatement(statement);
        m_complexity++;
    }

    public void visitDoWhileStatement(PsiDoWhileStatement statement) {
        super.visitDoWhileStatement(statement);
        m_complexity++;
    }

    public void visitConditionalExpression(PsiConditionalExpression expression) {
        super.visitConditionalExpression(expression);
        m_complexity++;
    }

    public void visitSwitchStatement(PsiSwitchStatement statement) {
        super.visitSwitchStatement(statement);
        final PsiCodeBlock body = statement.getBody();
        if (body == null) {
            return;
        }
        final PsiStatement[] statements = body.getStatements();
        boolean pendingLabel = false;
        for (int i = 0; i < statements.length; i++) {
            final PsiStatement child = statements[i];
            if (child instanceof PsiSwitchLabelStatement) {
                if (!pendingLabel) {
                    m_complexity++;
                }
                pendingLabel = true;
            } else {
                pendingLabel = false;
            }
        }
    }

    public void visitWhileStatement(PsiWhileStatement statement) {
        super.visitWhileStatement(statement);
        m_complexity++;
    }

    public int getComplexity() {
        return m_complexity;
    }
}
