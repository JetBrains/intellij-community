package com.siyeh.ig.security;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

public class PublicStaticArrayFieldInspection extends FieldInspection {

    public String getDisplayName() {
        return "Public static array field";
    }

    public String getGroupDisplayName() {
        return GroupNames.SECURITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Public static array field #ref, compromising security #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new PublicStaticArrayFieldVisitor();
    }

    private static class PublicStaticArrayFieldVisitor extends BaseInspectionVisitor {
        public void visitField(@NotNull PsiField field){
            super.visitField(field);
            if(!field.hasModifierProperty(PsiModifier.PUBLIC)
                &&!field.hasModifierProperty(PsiModifier.STATIC))
            {
                return;
            }
            final PsiType type = field.getType();
            if(type== null)
            {
                return;
            }
            if(!(type instanceof PsiArrayType)){
                return;
            }
            registerFieldError(field);
        }

    }
}
