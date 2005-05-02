package com.siyeh.ig.abstraction;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.LibraryUtil;

import java.util.*;

class ClassAccessVisitor extends PsiRecursiveElementVisitor {
    private final Map<PsiClass,Integer> m_accessCounts = new HashMap<PsiClass, Integer>(2);
    private final Set<PsiClass> m_overAccessedClasses = new HashSet<PsiClass>(2);
    private final PsiClass currentClass;

    ClassAccessVisitor(PsiClass currentClass) {
        super();
        this.currentClass = currentClass;
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
        final Set<PsiClass> overAccessedClasses = m_overAccessedClasses;
        if(overAccessedClasses.contains(calledClass)){
            return;
        }

        if(LibraryUtil.classIsInLibrary(calledClass)){
            return;
        }

        if (PsiTreeUtil.isAncestor(currentClass, calledClass, true)) {
            return;
        }
        if (PsiTreeUtil.isAncestor(calledClass, currentClass, true)) {
            return;
        }
        PsiClass lexicallyEnclosingClass = currentClass;
        while(lexicallyEnclosingClass != null){
            if(lexicallyEnclosingClass.isInheritor(calledClass, true)){
                return;
            }
            lexicallyEnclosingClass = PsiTreeUtil.getParentOfType(lexicallyEnclosingClass,
                                                                             PsiClass.class);
        }
        final Map<PsiClass,Integer> accessCounts = m_accessCounts;
        final Integer count = accessCounts.get(calledClass);
        if (count == null) {
            accessCounts.put(calledClass, 1);
        } else if (count.equals(1)) {
            accessCounts.put(calledClass, 2);
        } else {
            overAccessedClasses.add(calledClass);
        }
    }

    public Set<PsiClass> getOveraccessedClasses() {
        return Collections.unmodifiableSet(m_overAccessedClasses);
    }
}
