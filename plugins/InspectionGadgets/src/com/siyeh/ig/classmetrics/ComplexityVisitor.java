package com.siyeh.ig.classmetrics;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

class ComplexityVisitor extends PsiRecursiveElementVisitor {
    private int m_complexity = 1;

    public void visitAnonymousClass(@NotNull PsiAnonymousClass aClass) {
        // to call to super, to keep this from drilling down
    }

    public void visitForStatement(@NotNull PsiForStatement statement) {
        super.visitForStatement(statement);
        m_complexity++;
    }

    public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
        super.visitForeachStatement(statement);
        m_complexity++;
    }

    public void visitIfStatement(@NotNull PsiIfStatement statement) {
        super.visitIfStatement(statement);
        m_complexity++;
    }

    public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
        super.visitDoWhileStatement(statement);
        m_complexity++;
    }

    public void visitConditionalExpression(PsiConditionalExpression expression) {
        super.visitConditionalExpression(expression);
        m_complexity++;
    }

    public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
        super.visitSwitchStatement(statement);
        final PsiCodeBlock body = statement.getBody();
        if (body == null) {
            return;
        }
        final PsiStatement[] statements = body.getStatements();
        boolean pendingLabel = false;
        for(final PsiStatement child : statements){
            if(child instanceof PsiSwitchLabelStatement){
                if(!pendingLabel){
                    m_complexity++;
                }
                pendingLabel = true;
            } else{
                pendingLabel = false;
            }
        }
    }

    public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
        super.visitWhileStatement(statement);
        m_complexity++;
    }

    public int getComplexity() {
        return m_complexity;
    }

    public void reset() {
        m_complexity = 1;
    }

}
