package com.siyeh.ig.psiutils;

import com.intellij.psi.*;

public class VariableUsedVisitor extends PsiRecursiveElementVisitor {
    private boolean used = false;
    private final PsiVariable variable;

    public VariableUsedVisitor(PsiVariable variable) {
        super();
        this.variable = variable;
    }

    public void visitReferenceExpression(PsiReferenceExpression ref) {
        super.visitReferenceExpression(ref);

        final PsiElement referent = ref.resolve();
        if (referent == null) {
            return;
        }
        if (referent.equals(variable)) {
            used = true;
        }
    }

    public boolean isUsed() {
        return used;
    }
}
