package com.siyeh.ig.serialization;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
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

    public BaseInspectionVisitor buildVisitor() {
        return new NonSerializableWithSerialVersionUIDVisitor();
    }

    private static class NonSerializableWithSerialVersionUIDVisitor extends BaseInspectionVisitor {

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
