package com.siyeh.ig.abstraction;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.LibraryUtil;

import java.util.*;

class ClassAccessVisitor extends PsiRecursiveElementVisitor {
    private final Map m_accessCounts = new HashMap(2);
    private final Set m_overAccessedClasses = new HashSet(2);
    private static final Integer ONE = new Integer(1);
    private static final Integer TWO = new Integer(2);
    private PsiClass currentClass;

    ClassAccessVisitor(PsiClass currentClass) {
        super();
        this.currentClass = currentClass;
    }

    public void visitReferenceExpression(PsiReferenceExpression ref) {
        final PsiExpression qualifier = ref.getQualifierExpression();
        if (qualifier != null) {
            qualifier.accept(this);
        }
        final PsiReferenceParameterList typeParameters = ref.getParameterList();
        if (typeParameters != null) {
            typeParameters.accept(this);
        }
    }

    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        final PsiMethod method = expression.resolveMethod();
        if (method == null) {
            return;
        }
        final PsiClass calledClass = method.getContainingClass();
        if (currentClass.equals(calledClass)) {
            return;
        }
        PsiClass lexicallyEnclosingClass = currentClass;
        while (lexicallyEnclosingClass != null) {
            if (lexicallyEnclosingClass.isInheritor(calledClass, true)) {
                return;
            }
            lexicallyEnclosingClass = (PsiClass)PsiTreeUtil.getParentOfType(lexicallyEnclosingClass, PsiClass.class);
        }
        if (PsiTreeUtil.isAncestor(currentClass, calledClass, true)) {
            return;
        }
        if (PsiTreeUtil.isAncestor(calledClass, currentClass, true)) {
            return;
        }
        if (LibraryUtil.classIsInLibrary(calledClass)) {
            return;
        }
        final Set overAccessedClasses = m_overAccessedClasses;
        if (overAccessedClasses.contains(calledClass)) {
            return;
        }
        final Map accessCounts = m_accessCounts;
        final Integer count = (Integer) accessCounts.get(calledClass);
        if (count == null) {
            accessCounts.put(calledClass, ONE);
        } else if (count.equals(ONE)) {
            accessCounts.put(calledClass, TWO);
        } else {
            overAccessedClasses.add(calledClass);
        }
    }

    public Set getOveraccessedClasses() {
        return Collections.unmodifiableSet(m_overAccessedClasses);
    }
}
