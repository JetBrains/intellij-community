package com.siyeh.ig.serialization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.InitializationUtils;
import com.siyeh.ig.psiutils.SerializationUtils;

public class ReadObjectInitializationInspection extends FieldInspection {
    public String getID(){
        return "InstanceVariableMayNotBeInitializedByReadObject";
    }
    public String getDisplayName() {
        return "Instance variable may not be initialized by 'readObject()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Instance variable #ref may not be initialized during call to readObject #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ReadObjectInitializationVisitor(this, inspectionManager, onTheFly);
    }

    private static class ReadObjectInitializationVisitor extends BaseInspectionVisitor {
        private ReadObjectInitializationVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            // no call to super, so it doesn't drill down
            final PsiClass aClass = method.getContainingClass();
            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            if (!SerializationUtils.isSerializable(aClass)) {
                return;
            }

            if (!SerializationUtils.isReadObject(method)) {
                return;
            }
            final PsiField[] fields = aClass.getFields();
            for (int i = 0; i < fields.length; i++) {
                final PsiField field = fields[i];
                if (!isFieldInitialized(field, method)) {
                    registerFieldError(field);
                }
            }

        }

        public static boolean isFieldInitialized(PsiField field, PsiMethod method) {
            if (field.hasModifierProperty(PsiModifier.STATIC)) {
                return true;
            }
            if (field.hasModifierProperty(PsiModifier.FINAL) && field.getInitializer() != null) {
                return true;
            }
            final PsiCodeBlock body = method.getBody();
            return InitializationUtils.blockMustAssignVariableOrFail(field, body);
        }

    }
}
