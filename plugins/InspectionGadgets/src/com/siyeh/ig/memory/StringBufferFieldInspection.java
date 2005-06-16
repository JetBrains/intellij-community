package com.siyeh.ig.memory;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

public class StringBufferFieldInspection extends FieldInspection {

    public String getDisplayName() {
        return "StringBuffer field";
    }

    public String getGroupDisplayName() {
        return GroupNames.MEMORY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiField field = (PsiField) location.getParent();
        assert field != null;
        final PsiType type = field.getType();
        final String typeName = type.getPresentableText();
        return typeName+ " field '#ref' #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StringBufferFieldVisitor();
    }

    private static class StringBufferFieldVisitor extends BaseInspectionVisitor {

        public void visitField(@NotNull PsiField field) {
            super.visitField(field);
            final PsiType type = field.getType();
            if(type == null)
            {
                return;
            }
            if (!type.equalsToText("java.lang.StringBuffer") &&
                        !type.equalsToText("java.lang.StringBuilder")) {
                return;
            }
            registerFieldError(field);

        }

    }

}
