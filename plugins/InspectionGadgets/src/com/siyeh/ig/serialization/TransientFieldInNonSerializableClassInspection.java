package com.siyeh.ig.serialization;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
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
        assert fieldModifierList != null;
        final PsiField field = (PsiField) fieldModifierList.getParent();
        assert field != null;
        return "Field " + field.getName() + " is marked '#ref', in non-Serializable class #loc ";
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class TransientFieldInNonSerializableClassFix extends InspectionGadgetsFix {
        public String getName() {
            return "Remove 'transient'";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiElement transientModifier = descriptor.getPsiElement();
            deleteElement(transientModifier);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new TransientFieldInNonSerializableClassVisitor();
    }

    private static class TransientFieldInNonSerializableClassVisitor extends BaseInspectionVisitor {
        private boolean m_inClass = false;


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
