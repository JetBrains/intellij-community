package com.siyeh.ig.confusing;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class LocalVariableAccessVisitor extends PsiRecursiveElementVisitor {
    private final Set<PsiElement> m_accesssedVariables = new HashSet<PsiElement>(2);

    public LocalVariableAccessVisitor() {
        super();
    }

    public void visitReferenceExpression(@NotNull PsiReferenceExpression ref) {
        super.visitReferenceExpression(ref);
        final PsiExpression qualifier = ref.getQualifierExpression();
        if (qualifier != null && !(qualifier instanceof PsiThisExpression)) {
            return;
        }
        final PsiElement element = ref.resolve();
        if (!(element instanceof PsiLocalVariable)) {
            return;
        }
        m_accesssedVariables.add(element);
    }

    public Set<PsiElement> getAccessedVariables() {
        return Collections.unmodifiableSet(m_accesssedVariables);
    }
}
