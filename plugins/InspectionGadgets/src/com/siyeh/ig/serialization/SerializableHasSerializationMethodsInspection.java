package com.siyeh.ig.serialization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.SerializationUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;

import javax.swing.*;

public class SerializableHasSerializationMethodsInspection extends ClassInspection {
    public boolean m_ignoreSerializableDueToInheritance = false;

    public String getDisplayName() {
        return "Serializable class without 'readObject()' and 'writeObject()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiClass aClass = (PsiClass) location.getParent();
        final PsiMethod[] methods = aClass.getMethods();
        boolean hasReadObject = false;
        boolean hasWriteObject = false;
        for (int i = 0; i < methods.length; i++) {
            final PsiMethod method = methods[i];
            if (SerializationUtils.isReadObject(method)) {
                hasReadObject = true;
            } else if (SerializationUtils.isWriteObject(method)) {
                hasWriteObject = true;
            }
        }
        if (!hasReadObject && !hasWriteObject) {
            return "#ref doesn't define readObject() or writeObject() #loc";
        } else if (hasReadObject) {
            return "#ref doesn't define writeObject() #loc";
        } else {
            return "#ref doesn't define readObject() #loc";
        }
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Ignore classes serializable due to inheritance",
                this, "m_ignoreSerializableDueToInheritance");
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SerializableDefinesMethodsVisitor(this, inspectionManager, onTheFly);
    }

    private class SerializableDefinesMethodsVisitor extends BaseInspectionVisitor {
        private SerializableDefinesMethodsVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
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
            final PsiMethod[] methods = aClass.getMethods();
            boolean hasReadObject = false;
            boolean hasWriteObject = false;
            for (int i = 0; i < methods.length; i++) {
                final PsiMethod method = methods[i];
                if (SerializationUtils.isReadObject(method)) {
                    hasReadObject = true;
                } else if (SerializationUtils.isWriteObject(method)) {
                    hasWriteObject = true;
                }
            }
            if (hasWriteObject && hasReadObject) {
                return;
            }
            registerClassError(aClass);
        }

    }

}
