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
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;

import javax.swing.*;

public class SerializableHasSerialVersionUIDFieldInspection extends ClassInspection {
    public boolean m_ignoreSerializableDueToInheritance = true;

    public String getDisplayName() {
        return "Serializable class without serialVersionUID";
    }

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref doesn't define a serialVersionUID field #loc";
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Ignore classes serializable due to inheritance",
                this, "m_ignoreSerializableDueToInheritance");
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SerializableDefinesSerialVersionUIDVisitor(this, inspectionManager, onTheFly);
    }

    private class SerializableDefinesSerialVersionUIDVisitor extends BaseInspectionVisitor {
        private SerializableDefinesSerialVersionUIDVisitor(BaseInspection inspection,
                                                           InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            // no call to super, so it doesn't drill down

            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            if (m_ignoreSerializableDueToInheritance) {
                if (!SerializationUtils.isDirectlySerializable(aClass)) {
                    return;
                }
            } else {
                if (!SerializationUtils.isSerializable(aClass)) {
                    return;
                }
            }
            final PsiField[] fields = aClass.getFields();
            boolean hasSerialVersionUID = false;
            for (int i = 0; i < fields.length; i++) {
                final PsiField field = fields[i];
                if (isSerialVersionUID(field)) {
                    hasSerialVersionUID = true;
                }
            }
            if (hasSerialVersionUID) {
                return;
            }
            registerClassError(aClass);
        }

        private boolean isSerialVersionUID(PsiField field) {
            final String methodName = field.getName();
            return "serialVersionUID".equals(methodName);
        }

    }

}
