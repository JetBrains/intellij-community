package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.RemoveModifierFix;

public class PublicConstructorInNonPublicClassInspection extends MethodInspection {
    public String getDisplayName() {
        return "'public' constructor in non-'public' class";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiModifierList modifiers = (PsiModifierList) location.getParent();
        final PsiMethod meth = (PsiMethod) modifiers.getParent();
        return "Constructor is declared '#ref' in non-'public' class " + meth.getName() + " #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new PublicConstructorInNonPublicClassVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new RemoveModifierFix(location);
    }


    private static class PublicConstructorInNonPublicClassVisitor extends BaseInspectionVisitor {
        private PublicConstructorInNonPublicClassVisitor(BaseInspection inspection,
                                                         InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            //no call to super, so we don't drill into anonymous classes
            if (!method.isConstructor()) {
                return;
            }
            if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (containingClass.hasModifierProperty(PsiModifier.PUBLIC)) {
                return;
            }
            registerModifierError(PsiModifier.PUBLIC, method);

        }

    }

}
