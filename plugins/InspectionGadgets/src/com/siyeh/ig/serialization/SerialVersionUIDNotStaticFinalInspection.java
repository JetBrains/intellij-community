package com.siyeh.ig.serialization;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

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

    public BaseInspectionVisitor buildVisitor() {
        return new SerializableDefinesSerialVersionUIDVisitor();
    }

    private static class SerializableDefinesSerialVersionUIDVisitor extends BaseInspectionVisitor {
        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so it doesn't drill down
            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            PsiField badSerialVersionUIDField = null;
            final PsiField[] fields = aClass.getFields();
            for(final PsiField field : fields){
                if(isSerialVersionUID(field)){
                    if(!field.hasModifierProperty(PsiModifier.STATIC) ||
                            !field.hasModifierProperty(PsiModifier.PRIVATE) ||
                            !field.hasModifierProperty(PsiModifier.FINAL)){
                        badSerialVersionUIDField = field;
                        break;
                    } else{
                        final PsiType type = field.getType();
                        if(!PsiType.LONG.equals(type)){
                            badSerialVersionUIDField = field;
                            break;
                        }
                    }
                }
            }
            if(badSerialVersionUIDField == null)
            {
                return;
            }
            if(!SerializationUtils.isSerializable(aClass)){
                return;
            }
            registerFieldError(badSerialVersionUIDField);
        }

        private static boolean isSerialVersionUID(PsiField field) {
            final String fieldName = field.getName();
            return "serialVersionUID".equals(fieldName);
        }

    }

}
