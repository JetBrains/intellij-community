package com.siyeh.ig.classlayout;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.RemoveModifierFix;
import org.jetbrains.annotations.NotNull;

public class FinalClassInspection extends MethodInspection {

    public String getDisplayName() {
        return "'final' class";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Class declared '#ref' #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new FinalStaticMethodVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new RemoveModifierFix(location);
    }

    private static class FinalStaticMethodVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            //no call to super, so we don't drill into inner classes
            if (!aClass.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            registerModifierError(PsiModifier.FINAL, aClass);
        }

    }

}
