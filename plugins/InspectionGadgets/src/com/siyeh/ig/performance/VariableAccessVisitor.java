package com.siyeh.ig.performance;

import com.intellij.psi.*;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class VariableAccessVisitor extends PsiRecursiveElementVisitor {
    private final Map<PsiElement,Integer> m_accessCounts = new HashMap<PsiElement, Integer>(2);
    private final Set<PsiElement> m_overAccessedFields = new HashSet<PsiElement>(2);
    private static final Integer ONE = 1;
    private static final Integer TWO = 2;

    VariableAccessVisitor() {
        super();
    }

    public void visitReferenceExpression(@NotNull PsiReferenceExpression ref) {
        super.visitReferenceExpression(ref);
        final PsiExpression qualifier = ref.getQualifierExpression();

        if (qualifier != null && !(qualifier instanceof PsiThisExpression)) {
            return;
        }
        final PsiElement element = ref.resolve();
        if (!(element instanceof PsiField)) {
            return;
        }
        final Set<PsiElement> overAccessedFields = m_overAccessedFields;
        if (overAccessedFields.contains(element)) {
            return;
        }
        if (ControlFlowUtils.isInLoop(element)) {
            overAccessedFields.add(element);
        }
        final Map<PsiElement,Integer> accessCounts = m_accessCounts;
        final Integer count = accessCounts.get(element);
        if (count == null) {
            accessCounts.put(element, ONE);
        } else if (count.equals(ONE)) {
            accessCounts.put(element, TWO);
        } else {
            overAccessedFields.add(element);
        }
    }

    public Set<PsiElement> getOveraccessedFields() {
        return Collections.unmodifiableSet(m_overAccessedFields);
    }
}
