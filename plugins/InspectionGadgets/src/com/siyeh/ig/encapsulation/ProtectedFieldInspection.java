package com.siyeh.ig.encapsulation;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.EncapsulateVariableFix;
import org.jetbrains.annotations.NotNull;

public class ProtectedFieldInspection extends FieldInspection {
    private final EncapsulateVariableFix fix = new EncapsulateVariableFix();

    public String getDisplayName() {
        return "Protected field";
    }

    public String getGroupDisplayName() {
        return GroupNames.ENCAPSULATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Protected field #ref #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ProtectedFieldVisitor();
    }

    private static class ProtectedFieldVisitor extends BaseInspectionVisitor {


        public void visitField(@NotNull PsiField field) {
            if (!field.hasModifierProperty(PsiModifier.PROTECTED)) {
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
