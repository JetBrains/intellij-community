package com.siyeh.ig.performance;

import com.intellij.psi.*;
import com.siyeh.ig.psiutils.ControlFlowUtils;

import java.util.*;

class VariableAccessVisitor extends PsiRecursiveElementVisitor {
    private final Map m_accessCounts = new HashMap(2);
    private final Set m_overAccessedFields = new HashSet(2);
    private static final Integer ONE = new Integer(1);
    private static final Integer TWO = new Integer(2);

    VariableAccessVisitor() {
        super();
    }

    public void visitReferenceExpression(PsiReferenceExpression ref) {
        super.visitReferenceExpression(ref);
        final PsiExpression qualifier = ref.getQualifierExpression();

        if (qualifier != null && !(qualifier instanceof PsiThisExpression)) {
            return;
        }
        final PsiElement element = ref.resolve();
        if (!(element instanceof PsiField)) {
            return;
        }
        final Set overAccessedFields = m_overAccessedFields;
        if (overAccessedFields.contains(element)) {
            return;
        }
        if (ControlFlowUtils.isInLoop(element)) {
            overAccessedFields.add(element);
        }
        final Map accessCounts = m_accessCounts;
        final Integer count = (Integer) accessCounts.get(element);
        if (count == null) {
            accessCounts.put(element, ONE);
        } else if (count.equals(ONE)) {
            accessCounts.put(element, TWO);
        } else {
            overAccessedFields.add(element);
        }
    }

    public Set getOveraccessedFields() {
        return Collections.unmodifiableSet(m_overAccessedFields);
    }
}
