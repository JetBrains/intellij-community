package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.RemoveModifierFix;

public class FinalPrivateMethodInspection extends MethodInspection {
    public String getDisplayName() {
        return "'private' method declared 'final'";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "'private' method declared '#ref' #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new FinalStaticMethodVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new RemoveModifierFix(location);
    }

    private static class FinalStaticMethodVisitor extends BaseInspectionVisitor {
        private FinalStaticMethodVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            //no call to super, so we don't drill into anonymous classes
            if (!method.hasModifierProperty(PsiModifier.FINAL)
                    || !method.hasModifierProperty(PsiModifier.PRIVATE)) {
                return;
            }
            registerModifierError(PsiModifier.FINAL, method);

        }

    }

}
