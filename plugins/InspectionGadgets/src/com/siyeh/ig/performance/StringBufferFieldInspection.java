package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import com.siyeh.ig.GroupNames;

public class StringBufferFieldInspection extends FieldInspection {

    public String getDisplayName() {
        return "StringBuffer field";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiField field = (PsiField) location.getParent();
        final PsiType type = field.getType();
        final String typeName = type.getPresentableText();
        return typeName+ " field '#ref' #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new StringBufferFieldVisitor(this, inspectionManager, onTheFly);
    }

    private static class StringBufferFieldVisitor extends BaseInspectionVisitor {
        private StringBufferFieldVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitField(PsiField field) {
            super.visitField(field);
            final PsiType type = field.getType();
            if(type == null)
            {
                return;
            }
            final String text = type.getCanonicalText();
            if (!"java.lang.StringBuffer".equals(text) &&
                    !"java.lang.StringBuilder".equals(text)) {
                return;
            }
            registerFieldError(field);

        }

    }

}
