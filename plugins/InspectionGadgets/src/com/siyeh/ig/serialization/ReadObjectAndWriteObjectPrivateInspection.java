package com.siyeh.ig.serialization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.MakePrivateFix;
import com.siyeh.ig.psiutils.SerializationUtils;

public class ReadObjectAndWriteObjectPrivateInspection extends MethodInspection {
    private final MakePrivateFix fix = new MakePrivateFix();

    public String getID(){
        return "NonPrivateSerializationMethod";
    }
    public String getDisplayName() {
        return "'readObject()' or 'writeObject()' not declared 'private'";
    }

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref not declared 'private' #loc ";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ReadObjectWriteObjectPrivateVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class ReadObjectWriteObjectPrivateVisitor extends BaseInspectionVisitor {
        private ReadObjectWriteObjectPrivateVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
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
            if (!SerializationUtils.isReadObject(method) &&
                    !SerializationUtils.isWriteObject(method)) {
                return;
            }
            if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
                return;
            }
            registerMethodError(method);
        }
    }

}
