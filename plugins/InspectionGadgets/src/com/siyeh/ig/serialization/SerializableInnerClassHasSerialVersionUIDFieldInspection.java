package com.siyeh.ig.serialization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.AddSerialVersionUIDFix;
import com.siyeh.ig.psiutils.SerializationUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;

import javax.swing.*;

public class SerializableInnerClassHasSerialVersionUIDFieldInspection extends ClassInspection {
    public boolean m_ignoreSerializableDueToInheritance = true;
    private final AddSerialVersionUIDFix fix = new AddSerialVersionUIDFix();

    public String getDisplayName() {
        return "Serializable non-static inner class without serialVersionUID";
    }

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Inner class #ref doesn't define a serialVersionUID field #loc";
    }
    
    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
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
            final PsiClass containingClass = aClass.getContainingClass();
            if(containingClass==null)
            {
                return;
            }
            if(aClass.hasModifierProperty(PsiModifier.STATIC))
            {
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
            if (hasSerialVersionUIDField(aClass)) {
                return;
            }
            registerClassError(aClass);
        }

        private boolean hasSerialVersionUIDField(PsiClass aClass) {
            final PsiField[] fields = aClass.getFields();
            boolean hasSerialVersionUID = false;
            for (int i = 0; i < fields.length; i++) {
                final String fieldName = fields[i].getName();
                if ("serialVersionUID".equals(fieldName)) {
                    hasSerialVersionUID = true;
                }
            }
            return hasSerialVersionUID;
        }

    }

}
