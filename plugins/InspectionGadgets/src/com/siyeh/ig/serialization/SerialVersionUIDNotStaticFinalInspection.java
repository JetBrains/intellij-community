package com.siyeh.ig.serialization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.SerializationUtils;

public class SerialVersionUIDNotStaticFinalInspection extends ClassInspection {
    public String getID(){
        return "SerialVersionUIDWithWrongSignature";
    }
    public String getDisplayName() {
        return "'serialVersionUID' field not declared 'private static final long'";
    }

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref field of a Serializable class is not declared 'private static final long' #loc ";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SerializableDefinesSerialVersionUIDVisitor(this, inspectionManager, onTheFly);
    }

    private static class SerializableDefinesSerialVersionUIDVisitor extends BaseInspectionVisitor {
        private SerializableDefinesSerialVersionUIDVisitor(BaseInspection inspection,
                                                           InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            // no call to super, so it doesn't drill down
            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            if (!SerializationUtils.isSerializable(aClass)) {
                return;
            }
            final PsiField[] fields = aClass.getFields();
            for (int i = 0; i < fields.length; i++) {
                final PsiField field = fields[i];
                if (isSerialVersionUID(field)) {
                    if (!field.hasModifierProperty(PsiModifier.STATIC) ||
                            !field.hasModifierProperty(PsiModifier.PRIVATE) ||
                            !field.hasModifierProperty(PsiModifier.FINAL)) {
                        registerFieldError(field);
                    } else {
                        final PsiType type = field.getType();
                        if (!PsiType.LONG.equals(type)) {
                            registerFieldError(field);
                        }
                    }
                }
            }
        }

        private static boolean isSerialVersionUID(PsiField field) {
            final String fieldName = field.getName();
            return "serialVersionUID".equals(fieldName);
        }

    }

}
