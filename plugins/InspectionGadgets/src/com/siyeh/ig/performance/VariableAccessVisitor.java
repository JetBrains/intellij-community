package com.siyeh.ig.performance;

import com.intellij.psi.*;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class VariableAccessVisitor extends PsiRecursiveElementVisitor {
    private final Map<PsiField,Integer> m_accessCounts = new HashMap<PsiField, Integer>(2);
    private final Set<PsiField> m_overAccessedFields = new HashSet<PsiField>(2);

    public VariableAccessVisitor() {
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
        final PsiField field = (PsiField) element;
        final Set<PsiField> overAccessedFields = m_overAccessedFields;
        if (overAccessedFields.contains(field)) {
            return;
        }
        if (ControlFlowUtils.isInLoop(field)) {
            overAccessedFields.add(field);
        }
        final Map<PsiField,Integer> accessCounts = m_accessCounts;
        final Integer count = accessCounts.get(field);
        if (count == null) {
            accessCounts.put(field, 1);
        } else if (count == 1) {
            accessCounts.put(field, 2);
        } else {
            overAccessedFields.add(field);
        }
    }

    public Set<PsiField> getOveraccessedFields() {
        return Collections.unmodifiableSet(m_overAccessedFields);
    }
}
