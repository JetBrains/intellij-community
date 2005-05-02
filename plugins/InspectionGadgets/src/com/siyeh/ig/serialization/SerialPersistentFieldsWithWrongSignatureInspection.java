package com.siyeh.ig.serialization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.SerializationUtils;

public class SerialPersistentFieldsWithWrongSignatureInspection extends ClassInspection {

    public String getDisplayName() {
        return "'serialPersistentFields' field not declared 'private static final ObjectStreamField[]'";
    }

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref field of a Serializable class is not declared 'private static final ObjectStreamField[]' #loc ";
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

            PsiField badSerialPersistentFields = null;
            final PsiField[] fields = aClass.getFields();
            for(final PsiField field : fields){
                if(isSerialPersistentFields(field)){

                    if(!field.hasModifierProperty(PsiModifier.PRIVATE) ||
                            !field.hasModifierProperty(PsiModifier.STATIC) ||
                            !field.hasModifierProperty(PsiModifier.FINAL)){
                        badSerialPersistentFields = field;
                        break;
                    } else{
                        final PsiType type = field.getType();
                        if(type != null){
                            badSerialPersistentFields = field;
                            break;
                        }
                    }
                }
            }
            if(badSerialPersistentFields == null)
            {
                return;
            }
            if(!SerializationUtils.isSerializable(aClass)){
                return;
            }
            registerFieldError(badSerialPersistentFields);
        }

        private static boolean isSerialPersistentFields(PsiField field) {
            final String fieldName = field.getName();
            return "serialPersistentFields".equals(fieldName);
        }

    }

}
