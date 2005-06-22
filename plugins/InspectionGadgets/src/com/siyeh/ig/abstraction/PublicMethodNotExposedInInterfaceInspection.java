package com.siyeh.ig.abstraction;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import org.jetbrains.annotations.NotNull;

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

    public BaseInspectionVisitor buildVisitor() {
        return new PublicMethodNotExposedInInterface();
    }

    private static class PublicMethodNotExposedInInterface extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
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
            for(final PsiMethod superMethod : superMethods){
                final PsiClass superClass = superMethod.getContainingClass();
                if(superClass.isInterface()){
                    return true;
                }
                final String superclassName = superClass.getQualifiedName();
                if("java.lang.Object".equals(superclassName)){
                    return true;
                }
                if(exposedInInterface(superMethod)){
                    return true;
                }
            }
            return false;
        }

    }

}
