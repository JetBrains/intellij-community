package com.siyeh.ig.serialization;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.SerializationUtils;

public class ReadResolveAndWriteReplaceProtectedInspection extends MethodInspection {
    private static final Logger s_logger =
            Logger.getInstance("ReadResolveAndWriteReplaceProtectedInspection ");
    private final MakeProtectedFix fix = new MakeProtectedFix();

    public String getDisplayName() {
        return "'readResolve()' or 'writeReplace()' not declared 'protected'";
    }

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref() not declared 'protected' #loc";

    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ReadResolveWriteReplaceProtectedVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class MakeProtectedFix extends InspectionGadgetsFix {
        public String getName() {
            return "Make 'protected'";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            try {
                final PsiElement methodNameToken = descriptor.getPsiElement();
                final PsiMethod method =
                        (PsiMethod) methodNameToken.getParent();
                final PsiModifierList modifiers = method.getModifierList();
                modifiers.setModifierProperty(PsiModifier.PUBLIC, false);
                modifiers.setModifierProperty(PsiModifier.PRIVATE, false);
                modifiers.setModifierProperty(PsiModifier.PROTECTED, true);
            } catch (IncorrectOperationException e) {
                s_logger.error(e);
            }
        }
    }

    private static class ReadResolveWriteReplaceProtectedVisitor extends BaseInspectionVisitor {
        private ReadResolveWriteReplaceProtectedVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
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

            if (!SerializationUtils.isReadResolve(method) &&
                    !SerializationUtils.isWriteReplace(method)) {
                return;
            }
            if (method.hasModifierProperty(PsiModifier.PROTECTED)) {
                return;
            }
            registerMethodError(method);
        }
    }

}
