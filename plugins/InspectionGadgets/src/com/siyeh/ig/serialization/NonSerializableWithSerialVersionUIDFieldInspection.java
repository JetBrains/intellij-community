package com.siyeh.ig.serialization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.MakeSerializableFix;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

public class NonSerializableWithSerialVersionUIDFieldInspection extends ClassInspection {
    private final MakeSerializableFix fix = new MakeSerializableFix();

    public String getID(){
        return "NonSerializableClassWithSerialVersionUID";
    }

    public String getDisplayName() {
        return "Non-serializable class with 'serialVersionUID'";
    }

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Non-serializable class #ref defines a serialVersionUID field #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new NonSerializableWithSerialVersionUIDVisitor(this, inspectionManager, onTheFly);
    }

    private static class NonSerializableWithSerialVersionUIDVisitor extends BaseInspectionVisitor {
        private NonSerializableWithSerialVersionUIDVisitor(BaseInspection inspection,
                                                           InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so it doesn't drill down

            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }

            final PsiField[] fields = aClass.getFields();
            boolean hasSerialVersionUID = false;
            for(final PsiField field : fields){
                if(isSerialVersionUID(field)){
                    hasSerialVersionUID = true;
                }
            }
            if (!hasSerialVersionUID) {
                return;
            }
            if(SerializationUtils.isSerializable(aClass)){
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
