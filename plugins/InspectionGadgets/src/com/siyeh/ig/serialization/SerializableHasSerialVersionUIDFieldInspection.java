package com.siyeh.ig.serialization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.project.Project;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.SerializationUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;

import javax.swing.*;

public class SerializableHasSerialVersionUIDFieldInspection extends ClassInspection {
    public boolean m_ignoreSerializableDueToInheritance = true;
    private final AddSerialVersionUIDFix fix = new AddSerialVersionUIDFix();

    public String getDisplayName() {
        return "Serializable class without serialVersionUID";
    }

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref doesn't define a serialVersionUID field #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class AddSerialVersionUIDFix extends InspectionGadgetsFix {
        public String getName() {
            return "Add serialVersionUIDField";
        }

        public void applyFix(Project project, ProblemDescriptor problemDescriptor) {
            final PsiElement classIdentifier = problemDescriptor.getPsiElement();
            final PsiElement aClass = classIdentifier.getParent();
            try {
                final PsiManager psiManager = aClass.getManager();
                final PsiElementFactory elementFactory = psiManager.getElementFactory();
                final long serialVersionUID = 1;
                final PsiField field = elementFactory.createFieldFromText("private static final long serialVersionUID = "+ serialVersionUID+"L;", aClass);
                aClass.add(field);
            } catch (IncorrectOperationException e) {
                e.printStackTrace();
            }
        }
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
