package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

public class VolatileArrayFieldInspection extends FieldInspection {

    public String getDisplayName() {
        return "Volatile array field";
    }

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiField field = (PsiField) location.getParent();
        final String typeString = field.getType().getPresentableText();
        return "Volatile field #ref of type " + typeString + " #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new VolatileArrayFieldVisitor(this, inspectionManager, onTheFly);
    }

    private static class VolatileArrayFieldVisitor extends BaseInspectionVisitor {
        private VolatileArrayFieldVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitField(@NotNull PsiField field) {
            super.visitField(field);
            if(!field.hasModifierProperty(PsiModifier.VOLATILE)    )
            {
                 return;
            }
            final PsiType type = field.getType();
            if(type == null)
            {
                return;
            }
            if(type.getArrayDimensions()!=0)
            {
                registerFieldError(field);
            }
        }
    }

}
