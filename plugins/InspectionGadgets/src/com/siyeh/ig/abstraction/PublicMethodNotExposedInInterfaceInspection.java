package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;

public class PublicMethodNotExposedInInterfaceInspection extends MethodInspection {

    public String getDisplayName() {
        return "Public method not exposed in interface";
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    protected String buildErrorString(PsiElement location) {
        return "Public method '#ref' is not exposed via an interface #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new PublicMethodNotExposedInInterface(this, inspectionManager, onTheFly);
    }

    private static class PublicMethodNotExposedInInterface extends BaseInspectionVisitor {
        private PublicMethodNotExposedInInterface(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            super.visitMethod(method);
            if (method.isConstructor()) {
                return;
            }
            if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
                return;
            }
            if (method.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (containingClass.isInterface()|| containingClass.isAnnotationType()) {
                return;
            }
            if (!containingClass.hasModifierProperty(PsiModifier.PUBLIC)) {
                return;
            }
            if (exposedInInterface(method)) {
                return;
            }
            registerMethodError(method);
        }

        private boolean exposedInInterface(PsiMethod method) {
            final PsiMethod[] superMethods = PsiSuperMethodUtil.findSuperMethods(method);
            for (int i = 0; i < superMethods.length; i++) {
                final PsiMethod superMethod = superMethods[i];
                final PsiClass superClass = superMethod.getContainingClass();
                if (superClass.isInterface()) {
                    return true;
                }
                final String superclassName = superClass.getQualifiedName();
                if ("java.lang.Object".equals(superclassName)) {
                    return true;
                }
                if (exposedInInterface(superMethod)) {
                    return true;
                }
            }
            return false;
        }

    }

}
