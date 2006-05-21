package com.siyeh.ig.serialization;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

public class NonSerializableFieldInSerializableClassInspection
        extends ClassInspection {

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "non.serializable.field.in.serializable.class.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NonSerializableFieldInSerializableClassVisitor();
    }

    private static class NonSerializableFieldInSerializableClassVisitor
            extends BaseInspectionVisitor {

        public void visitField(@NotNull PsiField field) {
            if (field.hasModifierProperty(PsiModifier.TRANSIENT)
                    || field.hasModifierProperty(PsiModifier.STATIC)) {
                return;
            }
            final PsiClass aClass = field.getContainingClass();
            if (!SerializationUtils.isSerializable(aClass)) {
                return;
            }
            if (SerializationUtils.isProbablySerializable(field.getType())) {
                return;
            }
            final boolean hasWriteObject =
                    SerializationUtils.hasWriteObject(aClass);
            if (hasWriteObject) {
                return;
            }
            registerFieldError(field);
        }
    }
}
