package com.siyeh.ig.serialization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.SerializationUtils;

public class ReadObjectAndWriteObjectPrivateInspection extends MethodInspection {
    private static final Logger s_logger = Logger.getInstance("ReadObjectAndWriteObjectPrivateInspection");
    private final MakePrivateFix fix = new MakePrivateFix();

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

    private static class MakePrivateFix extends InspectionGadgetsFix {
        public String getName() {
            return "Make 'private'";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            try {
                final PsiElement methodNameToken = descriptor.getPsiElement();
                final PsiMethod method = (PsiMethod) methodNameToken.getParent();
                final PsiModifierList modifiers = method.getModifierList();
                modifiers.setModifierProperty(PsiModifier.PUBLIC, false);
                modifiers.setModifierProperty(PsiModifier.PROTECTED, false);
                modifiers.setModifierProperty(PsiModifier.PRIVATE, true);
            } catch (IncorrectOperationException e) {
                s_logger.error(e);
            }
        }
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
