package com.siyeh.ig.serialization;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

public class ExternalizableWithSerializationMethodsInspection extends ClassInspection {
    public String getID(){
        return "ExternalizableClassWithSerializationMethods";
    }
    public String getDisplayName() {
        return "Externalizable class with 'readObject()' or 'writeObject()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiClass aClass = (PsiClass) location.getParent();
        assert aClass!=null;
        final boolean hasReadObject = SerializationUtils.hasReadObject(aClass);
        final boolean hasWriteObject = SerializationUtils.hasWriteObject(aClass);
        if (hasReadObject && hasWriteObject) {
            return "Externalizable class #ref defines readObject() and writeObject() #loc";
        } else if (hasWriteObject) {
            return "Externalizable class #ref defines writeObject() #loc";
        } else {
            return "Externalizable class #ref defines readObject() #loc";
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ExternalizableDefinesSerializationMethodsVisitor();
    }

    private static class ExternalizableDefinesSerializationMethodsVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so it doesn't drill down
            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            if (!SerializationUtils.isExternalizable(aClass)) {
                return;
            }
            final boolean hasReadObject = SerializationUtils.hasReadObject(aClass);
            final boolean hasWriteObject = SerializationUtils.hasWriteObject(aClass);
            if (!hasWriteObject && !hasReadObject) {
                return;
            }
            registerClassError(aClass);
        }

    }

}
