package com.siyeh.ig.performance;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;

class InnerClassReferenceVisitor extends PsiRecursiveElementVisitor {
    private PsiClass innerClass;
    private boolean referencesStaticallyAccessible = true;

    InnerClassReferenceVisitor(PsiClass innerClass) {
        super();
        this.innerClass = innerClass;
    }

    public boolean areReferenceStaticallyAccessible() {
        return referencesStaticallyAccessible;
    }

    private boolean isClassStaticallyAccessible(PsiClass aClass) {
        if (aClass.hasModifierProperty(PsiModifier.STATIC)) {
            return true;
        }
        if (InheritanceUtil.isInheritorOrSelf(innerClass, aClass, true)) {
            return true;
        }
        PsiClass classScope = aClass;
        final PsiClass outerClass = PsiTreeUtil.getParentOfType(innerClass, PsiClass.class);
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
        if(!referencesStaticallyAccessible)
        {
            return;
        }
        super.visitThisExpression(expression);
        final PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
        if(qualifier == null)
        {
            return;
        }
        referencesStaticallyAccessible = false;
    }

    public void visitReferenceElement(PsiJavaCodeReferenceElement referenceElement) {
        if(!referencesStaticallyAccessible){
            return;
        }
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
        referencesStaticallyAccessible &=
                aClass.hasModifierProperty(PsiModifier.STATIC);
    }

    public void visitReferenceExpression(PsiReferenceExpression referenceExpression) {
        if(!referencesStaticallyAccessible){
            return;
        }
        super.visitReferenceExpression(referenceExpression);
        final PsiExpression qualifier = referenceExpression.getQualifierExpression();


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
            referencesStaticallyAccessible &=
                    isClassStaticallyAccessible(containingClass);
        }
        if(element instanceof PsiLocalVariable || element instanceof PsiParameter)
        {
            final PsiElement containingMethod =
                    PsiTreeUtil.getParentOfType(referenceExpression, PsiMethod.class);
            final PsiElement referencedMethod =
                    PsiTreeUtil.getParentOfType(element, PsiMethod.class);
            if(!containingMethod.equals(referencedMethod))
            {
                referencesStaticallyAccessible = false;
            }
        }
    }
}