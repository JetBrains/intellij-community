package com.siyeh.ig.serialization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.SerializationUtils;

public class NonSerializableWithSerialVersionUIDFieldInspection extends ClassInspection {

    public String getDisplayName() {
        return "Non-serializable class with serialVersionUID";
    }

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Non-serializable class #ref defines a serialVersionUID field #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new NonSerializableWithSerialVersionUIDVisitor(this, inspectionManager, onTheFly);
    }

    private static class NonSerializableWithSerialVersionUIDVisitor extends BaseInspectionVisitor {
        private NonSerializableWithSerialVersionUIDVisitor(BaseInspection inspection,
                                                           InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            // no call to super, so it doesn't drill down

            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            if (SerializationUtils.isSerializable(aClass)) {
                return;
            }
            final PsiField[] fields = aClass.getFields();
            boolean hasSerialVersionUID = false;
            for (int i = 0; i < fields.length; i++) {
                final PsiField field = fields[i];
                if (isSerialVersionUID(field)) {
                    hasSerialVersionUID = true;
                }
            }
            if (!hasSerialVersionUID) {
                return;
            }
            registerClassError(aClass);
        }

        private static boolean isSerialVersionUID(PsiField field) {
            final String methodName = field.getName();
            return "serialVersionUID".equals(methodName);
        }

    }

}
