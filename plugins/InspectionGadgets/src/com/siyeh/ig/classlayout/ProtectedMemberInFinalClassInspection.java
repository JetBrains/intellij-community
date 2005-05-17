package com.siyeh.ig.classlayout;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.RemoveModifierFix;
import org.jetbrains.annotations.NotNull;

public class ProtectedMemberInFinalClassInspection extends MethodInspection {

    public String getDisplayName() {
        return "'protected' member in 'final' class";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Class member declared '#ref' in 'final' class #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ProtectedMemberInFinalClassVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new RemoveModifierFix(location);
    }

    private static class ProtectedMemberInFinalClassVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            //no call to super, so we don't drill into anonymous classes
            if (!method.hasModifierProperty(PsiModifier.PROTECTED)) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (!containingClass.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            if (methodOverrides(method)) {
                return;
            }
            registerModifierError(PsiModifier.PROTECTED, method);

        }


        private static boolean methodOverrides(PsiMethod meth) {
            final PsiMethod[] superMethods = PsiSuperMethodUtil.findSuperMethods(meth);
            return superMethods != null && superMethods.length != 0;
        }

        public void visitField(@NotNull PsiField field) {
            //no call to super, so we don't drill into anonymous classes
            if (!field.hasModifierProperty(PsiModifier.PROTECTED)) {
                return;
            }
            final PsiClass containingClass = field.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (!containingClass.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            registerModifierError(PsiModifier.PROTECTED, field);
        }

    }
}
