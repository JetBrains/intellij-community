package com.siyeh.ig.performance;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;

class InnerClassReferenceVisitor extends PsiRecursiveElementVisitor {
    private PsiClass m_innerClass;
    private boolean m_referencesStaticallyAccessible = true;

    InnerClassReferenceVisitor(PsiClass innerClass) {
        super();
        m_innerClass = innerClass;
    }

    public boolean areReferenceStaticallyAccessible() {
        return m_referencesStaticallyAccessible;
    }

    private boolean isClassStaticallyAccessible(PsiClass aClass) {
        if (aClass.hasModifierProperty(PsiModifier.STATIC)) {
            return true;
        }
        if (InheritanceUtil.isInheritorOrSelf(m_innerClass, aClass, true)) {
            return true;
        }
        PsiClass classScope = aClass;
        final PsiClass outerClass = (PsiClass) m_innerClass.getContext();
        while (classScope != null) {
            if (InheritanceUtil.isInheritorOrSelf(outerClass, classScope, true)) {
                return false;
            }
            final PsiElement scope = classScope.getScope();
            if (scope instanceof PsiClass) {
                classScope = (PsiClass) scope;
            } else {
                classScope = null;
            }
        }
        return true;
    }

    public void visitThisExpression(PsiThisExpression expression){
        super.visitThisExpression(expression);
        final PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
        if(qualifier == null)
        {
            return;
        }
        m_referencesStaticallyAccessible = false;
    }

    public void visitReferenceElement(PsiJavaCodeReferenceElement referenceElement) {
        super.visitReferenceElement(referenceElement);
        final PsiElement element = referenceElement.resolve();
        if (!(element instanceof PsiClass)) {
            return;
        }
        final PsiClass aClass = (PsiClass) element;
        final PsiElement scope = aClass.getScope();
        if (!(scope instanceof PsiClass)) {
            return;
        }
        m_referencesStaticallyAccessible &=
                aClass.hasModifierProperty(PsiModifier.STATIC);
    }

    public void visitReferenceExpression(PsiReferenceExpression referenceExpression) {
        final PsiExpression qualifier = referenceExpression.getQualifierExpression();
        if (qualifier != null) {
            qualifier.accept(this);
        }
        final PsiReferenceParameterList typeParameters = referenceExpression.getParameterList();
        if (typeParameters != null) {
            typeParameters.accept(this);
        }

        if (qualifier instanceof PsiSuperExpression) {
            return;
        }
        if (qualifier instanceof PsiReferenceExpression) {
            final PsiReferenceExpression expression = (PsiReferenceExpression) qualifier;
            final PsiElement resolvedExpression = expression.resolve();
            if (!(resolvedExpression instanceof PsiField) &&
                    !(resolvedExpression instanceof PsiMethod)) {
                return;
            }
        }
        final PsiElement element = referenceExpression.resolve();
        if (element instanceof PsiMethod || element instanceof PsiField) {
            final PsiMember member = (PsiMember) element;
            if (member.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            final PsiClass containingClass = member.getContainingClass();
            m_referencesStaticallyAccessible &=
                    isClassStaticallyAccessible(containingClass);
        }
    }
}