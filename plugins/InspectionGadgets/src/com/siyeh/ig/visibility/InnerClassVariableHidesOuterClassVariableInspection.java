package com.siyeh.ig.visibility;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.ClassUtils;

public class InnerClassVariableHidesOuterClassVariableInspection extends FieldInspection {
    private final RenameFix fix = new RenameFix();

    public String getDisplayName() {
        return "Inner class field hides outer class field";
    }

    public String getGroupDisplayName() {
        return GroupNames.VISIBILITY_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public String buildErrorString(PsiElement location) {
        return "Inner class field '#ref' hides outer class field #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new InnerClassVariableHidesOuterClassVariableVisitor(this, inspectionManager, onTheFly);
    }

    private static class InnerClassVariableHidesOuterClassVariableVisitor extends BaseInspectionVisitor {
        private InnerClassVariableHidesOuterClassVariableVisitor(BaseInspection inspection,
                                                                 InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitField(PsiField field) {
            final PsiClass aClass = field.getContainingClass();
            if (aClass == null) {
                return;
            }
            final String fieldName = field.getName();
            if ("serialVersionUID".equals(fieldName)) {
                return;    //special case
            }
            PsiClass ancestorClass =
                    ClassUtils.getContainingClass(aClass);
            while (ancestorClass != null) {
                if (ancestorClass.findFieldByName(fieldName, false) != null) {
                    registerFieldError(field);
                }
                ancestorClass =
                        ClassUtils.getContainingClass(ancestorClass);
            }
        }

    }

}
