package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.EncapsulateVariableFix;

public class PackageVisibleFieldInspection extends FieldInspection {
    private final EncapsulateVariableFix fix = new EncapsulateVariableFix();

    public String getDisplayName() {
        return "Package-visible field";
    }

    public String getGroupDisplayName() {
        return GroupNames.ENCAPSULATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Package-visible field #ref #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ProtectedFieldVisitor(this, inspectionManager, onTheFly);
    }

    private static class ProtectedFieldVisitor extends BaseInspectionVisitor {
        private ProtectedFieldVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitField(PsiField field) {
            if (field.hasModifierProperty(PsiModifier.PROTECTED) ||
                    field.hasModifierProperty(PsiModifier.PUBLIC) ||
                    field.hasModifierProperty(PsiModifier.PRIVATE)) {
                return;
            }
            if (field.hasModifierProperty(PsiModifier.STATIC) &&
                    field.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            registerFieldError(field);
        }

    }

}
