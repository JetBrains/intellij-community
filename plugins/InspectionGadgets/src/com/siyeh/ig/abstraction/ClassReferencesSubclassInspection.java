package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CollectionUtils;

public class ClassReferencesSubclassInspection extends ClassInspection {

    public String getDisplayName() {
        return "Class references one of its subclasses";
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement classRef) {
        final PsiClass containingClass =
                ClassUtils.getContainingClass(classRef);
        final String containingClassName = containingClass.getName();
        return "Class " + containingClassName + " references subclass #ref #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ClassReferencesSubclassVisitor(this, inspectionManager, onTheFly);
    }

    private static class ClassReferencesSubclassVisitor extends BaseInspectionVisitor {
        private boolean m_inClass = false;

        private ClassReferencesSubclassVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            final boolean wasInClass = m_inClass;
            if (!m_inClass) {

                m_inClass = true;
                super.visitClass(aClass);
            }
            m_inClass = wasInClass;
        }

        public void visitVariable(PsiVariable variable) {
            final PsiType type = variable.getType();
            if (type == null) {
                return;
            }
            final PsiType componentType = type.getDeepComponentType();
            if (!(componentType instanceof PsiClassType)) {
                return;
            }

            final PsiClassType classType = (PsiClassType) componentType;

            final PsiClass containingClass =
                    ClassUtils.getContainingClass(variable);
            if (!isSubclass(classType, containingClass)) {
                return;
            }
            final PsiElement typeElement = variable.getTypeElement();
            registerError(typeElement);
        }

        public void visitMethod(PsiMethod method) {
            super.visitMethod(method);
            final PsiType type = method.getReturnType();
            if (type == null) {
                return;
            }
            final PsiType componentType = type.getDeepComponentType();
            if (!(componentType instanceof PsiClassType)) {
                return;
            }
            final PsiClassType classType = (PsiClassType) componentType;
            final PsiClass containingClass = method.getContainingClass();
            if (!isSubclass(classType, containingClass)) {
                return;
            }
            if (!CollectionUtils.isCollectionClass(type)) {
                return;
            }
            final PsiTypeElement typeElement = method.getReturnTypeElement();
            registerError(typeElement);
        }

        public void visitInstanceOfExpression(PsiInstanceOfExpression exp) {
            super.visitInstanceOfExpression(exp);
            final PsiTypeElement typeElement = exp.getCheckType();
            if (typeElement == null) {
                return;
            }
            final PsiType type = typeElement.getType();
            if (type == null) {
                return;
            }
            final PsiType componentType = type.getDeepComponentType();
            if (!(componentType instanceof PsiClassType)) {
                return;
            }
            final PsiClassType classType = (PsiClassType) componentType;
            final PsiClass containingClass =
                    ClassUtils.getContainingClass(exp);
            if (!isSubclass(classType, containingClass)) {
                return;
            }
            registerError(typeElement);
        }

        public void visitTypeCastExpression(PsiTypeCastExpression exp) {
            super.visitTypeCastExpression(exp);
            final PsiTypeElement typeElement = exp.getCastType();
            if (typeElement == null) {
                return;
            }
            final PsiType type = typeElement.getType();
            if (type == null) {
                return;
            }
            final PsiType componentType = type.getDeepComponentType();
            if (!(componentType instanceof PsiClassType)) {
                return;
            }
            final PsiClassType classType = (PsiClassType) componentType;
            final PsiClass containingClass =
                    ClassUtils.getContainingClass(exp);
            if (!isSubclass(classType, containingClass)) {
                return;
            }
            registerError(typeElement);
        }

        public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression exp) {
            super.visitClassObjectAccessExpression(exp);
            final PsiTypeElement typeElement = exp.getOperand();
            if (typeElement == null) {
                return;
            }
            final PsiType type = typeElement.getType();
            if (type == null) {
                return;
            }
            final PsiType componentType = type.getDeepComponentType();
            if (!(componentType instanceof PsiClassType)) {
                return;
            }
            final PsiClassType classType = (PsiClassType) componentType;
            final PsiClass containingClass =
                    ClassUtils.getContainingClass(exp);
            if (!isSubclass(classType, containingClass)) {
                return;
            }
            registerError(typeElement);
        }

        private static boolean isSubclass(PsiClassType childClass, PsiClass parent) {
            final PsiClass child = childClass.resolve();
            if (child == null) {
                return false;
            }
            return child.isInheritor(parent, true);
        }
    }

}
