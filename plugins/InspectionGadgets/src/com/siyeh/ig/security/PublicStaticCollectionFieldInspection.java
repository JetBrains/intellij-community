package com.siyeh.ig.security;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import com.siyeh.ig.psiutils.CollectionUtils;
import org.jetbrains.annotations.NotNull;

public class PublicStaticCollectionFieldInspection extends FieldInspection {

    public String getDisplayName() {
        return "Public static collection field";
    }

    public String getGroupDisplayName() {
        return GroupNames.SECURITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Public static collection field #ref, compromising security #loc";
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
            if(!CollectionUtils.isCollectionClass(type)){
                return;
            }
            registerFieldError(field);
        }

    }
}
