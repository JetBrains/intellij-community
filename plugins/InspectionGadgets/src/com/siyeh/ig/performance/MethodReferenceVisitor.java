package com.siyeh.ig.performance;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;

class MethodReferenceVisitor extends PsiRecursiveElementVisitor {
    private boolean m_referencesStaticallyAccessible = true;
    private PsiMethod m_method;

    MethodReferenceVisitor(PsiMethod method) {
        super();
        m_method = method;
    }

    public boolean areReferencesStaticallyAccessible() {
        return m_referencesStaticallyAccessible;
    }

    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        final PsiElement parent =
                PsiTreeUtil.getParentOfType(reference, PsiNewExpression.class);
        if (parent == null) {
            return;
        }
        final PsiElement resolvedElement = reference.resolve();
        if (!(resolvedElement instanceof PsiClass)) {
            return;
        }
        final PsiClass aClass = (PsiClass) resolvedElement;
        final PsiElement scope = aClass.getScope();
        if (!(scope instanceof PsiClass)) {
            return;
        }
        m_referencesStaticallyAccessible &= aClass.hasModifierProperty(PsiModifier.STATIC);
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
        final PsiExpression qualifier = expression.getQualifierExpression();
        if (qualifier != null) {
            qualifier.accept(this);
        }
        final PsiReferenceParameterList typeParameters = expression.getParameterList();
        if (typeParameters != null) {
            typeParameters.accept(this);
        }
        final PsiElement element = expression.resolve();
        if (element instanceof PsiField) {
            m_referencesStaticallyAccessible &= isFieldStaticallyAccessible((PsiField) element);
        } else if (element instanceof PsiMethod) {
            m_referencesStaticallyAccessible &= isMethodStaticallyAccessible((PsiMethod) element);
        }
    }

    public void visitThisExpression(PsiThisExpression expression) {
        super.visitThisExpression(expression);
        m_referencesStaticallyAccessible = false;
    }

    private boolean isMethodStaticallyAccessible(PsiMethod method) {
        if (method.hasModifierProperty(PsiModifier.STATIC)) {
            return true;
        }
        if (method.isConstructor()) {
            return true;
        }
        final PsiClass referenceContainingClass = m_method.getContainingClass();
        final PsiClass methodContainingClass = method.getContainingClass();
        if (InheritanceUtil.isInheritorOrSelf(referenceContainingClass, methodContainingClass, true)) {
            return false;
        }
        return true;
    }

    boolean isFieldStaticallyAccessible(PsiField field) {
        if (field.hasModifierProperty(PsiModifier.STATIC)) {
            return true;
        }
        final PsiClass referenceContainingClass = m_method.getContainingClass();
        final PsiClass fieldContainingClass = field.getContainingClass();
        if (InheritanceUtil.isInheritorOrSelf(referenceContainingClass, fieldContainingClass, true)) {
            return false;
        }
        return true;
    }
}