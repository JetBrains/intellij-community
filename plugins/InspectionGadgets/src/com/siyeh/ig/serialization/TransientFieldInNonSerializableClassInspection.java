package com.siyeh.ig.serialization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

public class TransientFieldInNonSerializableClassInspection extends ClassInspection {
    private final TransientFieldInNonSerializableClassFix fix = new TransientFieldInNonSerializableClassFix();

    public String getDisplayName() {
        return "Transient field in non-serializable class";
    }

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiModifierList fieldModifierList = (PsiModifierList) location.getParent();
        final PsiField field = (PsiField) fieldModifierList.getParent();
        return "Field " + field.getName() + " is marked '#ref', in non-Serializable class #loc ";
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class TransientFieldInNonSerializableClassFix extends InspectionGadgetsFix {
        public String getName() {
            return "Remove 'transient'";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(descriptor)) return;
            final PsiElement transientModifier = descriptor.getPsiElement();
            deleteElement(transientModifier);
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new TransientFieldInNonSerializableClassVisitor(this, inspectionManager, onTheFly);
    }

    private static class TransientFieldInNonSerializableClassVisitor extends BaseInspectionVisitor {
        private boolean m_inClass = false;

        private TransientFieldInNonSerializableClassVisitor(BaseInspection inspection,
                                                            InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(@NotNull PsiClass aClass) {
            final boolean wasInClass = m_inClass;
            if (!m_inClass) {

                m_inClass = true;
                super.visitClass(aClass);
            }
            m_inClass = wasInClass;
        }

        public void visitField(@NotNull PsiField field) {
            if (!field.hasModifierProperty(PsiModifier.TRANSIENT)) {
                return;
            }
            final PsiClass aClass = field.getContainingClass();
            if (SerializationUtils.isSerializable(aClass)) {
                return;
            }
            registerModifierError(PsiModifier.TRANSIENT, field);
        }
    }

}
