package com.siyeh.ig.naming;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NotNull;

public class UpperCaseFieldNameNotConstantInspection extends FieldInspection {
    private final RenameFix fix = new RenameFix();

    public String getID(){
        return "NonConstantFieldWithUpperCaseName";
    }

    public String getDisplayName() {
        return "Non-constant field with upper-case name";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public String getGroupDisplayName() {
        return GroupNames.NAMING_CONVENTIONS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Non-constant field '#ref' with constant-style name #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ExceptionNameDoesntEndWithExceptionVisitor();
    }

    private static class ExceptionNameDoesntEndWithExceptionVisitor extends BaseInspectionVisitor {


        public void visitField(@NotNull PsiField field) {
            super.visitField(field);
            if(field.hasModifierProperty(PsiModifier.STATIC) &&
                    field.hasModifierProperty(PsiModifier.FINAL))
            {
                return;
            }
            final String fieldName = field.getName();
            if(!fieldName.toUpperCase().equals(fieldName))
            {
               return;
            }
            registerFieldError(field);
        }

    }

}
