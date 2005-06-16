package com.siyeh.ig.classlayout;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.RemoveModifierFix;
import org.jetbrains.annotations.NotNull;

public class PublicConstructorInNonPublicClassInspection extends MethodInspection {
    public String getDisplayName() {
        return "'public' constructor in non-'public' class";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiModifierList modifiers = (PsiModifierList) location.getParent();
        assert modifiers != null;
        final PsiMethod meth = (PsiMethod) modifiers.getParent();
        assert meth != null;
        return "Constructor is declared '#ref' in non-'public' class " + meth.getName() + " #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new PublicConstructorInNonPublicClassVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new RemoveModifierFix(location);
    }


    private static class PublicConstructorInNonPublicClassVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
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
