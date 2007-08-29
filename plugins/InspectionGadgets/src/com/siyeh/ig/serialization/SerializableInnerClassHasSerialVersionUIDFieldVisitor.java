package com.siyeh.ig.serialization;

import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.SerializationUtils;
import com.siyeh.HardcodedMethodConstants;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiField;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
*/
class SerializableInnerClassHasSerialVersionUIDFieldVisitor
        extends BaseInspectionVisitor {
  private SerializableInspection myInspection;

  public SerializableInnerClassHasSerialVersionUIDFieldVisitor(final SerializableInspection inspection) {
    myInspection = inspection;
  }

  public void visitClass(@NotNull PsiClass aClass) {
        // no call to super, so it doesn't drill down
        if (aClass.isInterface() || aClass.isAnnotationType() ||
                aClass.isEnum()) {
            return;
        }
        if (hasSerialVersionUIDField(aClass)) {
            return;
        }
        final PsiClass containingClass = aClass.getContainingClass();
        if (containingClass == null) {
            return;
        }
        if (aClass.hasModifierProperty(PsiModifier.STATIC)) {
            return;
        }
        if (!SerializationUtils.isSerializable(aClass)) {
            return;
        }
        if (myInspection.isIgnoredSubclass(aClass)) {
            return;
        }
        registerClassError(aClass);
    }

    private boolean hasSerialVersionUIDField(PsiClass aClass) {
        final PsiField[] fields = aClass.getFields();
        boolean hasSerialVersionUID = false;
        for (PsiField field : fields) {
            final String fieldName = field.getName();
            if (HardcodedMethodConstants.SERIAL_VERSION_UID.equals(
                    fieldName)) {
                hasSerialVersionUID = true;
            }
        }
        return hasSerialVersionUID;
    }
}