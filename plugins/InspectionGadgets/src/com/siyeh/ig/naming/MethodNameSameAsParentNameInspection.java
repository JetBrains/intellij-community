package com.siyeh.ig.naming;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.RenameFix;

public class MethodNameSameAsParentNameInspection extends MethodInspection {
    private final RenameFix fix = new RenameFix();

    public String getDisplayName() {
        return "Method name same as parent class name";
    }

    public String getGroupDisplayName() {
        return GroupNames.NAMING_CONVENTIONS_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public String buildErrorString(PsiElement location) {
        return "Method name '#ref' is the same as it's parent class name #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new MethodNameSameAsParentClassNameVisitor(this, inspectionManager, onTheFly);
    }

    private static class MethodNameSameAsParentClassNameVisitor extends BaseInspectionVisitor {
        private MethodNameSameAsParentClassNameVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            // no call to super, so it doesn't drill down into inner classes
            final String methodName = method.getName();
            if (methodName == null) {
                return;
            }

            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            final PsiClass parent = containingClass.getSuperClass();
            if (parent == null) {
                return;
            }
            final String parentName = parent.getName();
            if (parentName == null) {
                return;
            }
            if (!methodName.equals(parentName)) {
                return;
            }
            registerMethodError(method);
        }

    }

}
