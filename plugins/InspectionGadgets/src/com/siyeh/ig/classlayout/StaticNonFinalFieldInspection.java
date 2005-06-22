package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import org.jetbrains.annotations.NotNull;

public class StaticNonFinalFieldInspection extends FieldInspection {

    public String getDisplayName() {
        return "'static', non-'final' field";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Static non-final field #ref #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StaticNonFinalFieldVisitor();
    }

    private static class StaticNonFinalFieldVisitor extends BaseInspectionVisitor {

        public void visitField(@NotNull PsiField field) {
            if (!field.hasModifierProperty(PsiModifier.STATIC) ||
                    field.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            registerFieldError(field);
        }

    }
}
