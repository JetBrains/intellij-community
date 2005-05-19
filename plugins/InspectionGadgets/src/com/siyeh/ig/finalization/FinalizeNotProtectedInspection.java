package com.siyeh.ig.finalization;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import org.jetbrains.annotations.NotNull;

public class FinalizeNotProtectedInspection extends MethodInspection {
    private final ProtectedFinalizeFix fix = new ProtectedFinalizeFix();

    public String getDisplayName() {
        return "'finalize()' not declared 'protected'";
    }

    public String getGroupDisplayName() {
        return GroupNames.FINALIZATION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref() not declared 'protected' #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new FinalizeDeclaredProtectedVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class ProtectedFinalizeFix extends InspectionGadgetsFix {
        public String getName() {
            return "Make 'protected'";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiElement methodName = descriptor.getPsiElement();
            final PsiMethod method = (PsiMethod) methodName.getParent();
                final PsiModifierList modifiers = method.getModifierList();
                modifiers.setModifierProperty(PsiModifier.PUBLIC, false);
                modifiers.setModifierProperty(PsiModifier.PRIVATE, false);
                modifiers.setModifierProperty(PsiModifier.PROTECTED, true);
        }
    }

    private static class FinalizeDeclaredProtectedVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            //note: no call to super;
            final String methodName = method.getName();
            if (!"finalize".equals(methodName)) {
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParameters().length != 0) {
                return;
            }
            if (method.hasModifierProperty(PsiModifier.PROTECTED)) {
                return;
            }
            registerMethodError(method);
        }
    }

}
