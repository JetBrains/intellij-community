package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;

import javax.swing.*;

public class NonProtectedConstructorInAbstractClassInspection extends MethodInspection {
    public boolean m_ignoreNonPublicClasses = false;
    private static final Logger s_logger =
            Logger.getInstance("NonProtectedConstructorInAbstractClassInspection");
    private final MakeProtectedFix fix = new MakeProtectedFix();

    public String getDisplayName() {
        return "Constructor not 'protected' in 'abstract' class";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLASSLAYOUT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Constructor '#ref' is not declared 'protected' in 'abstract' class #loc";
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Ignore for non-public classes",
                this, "m_ignoreNonPublicClasses");
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new NonProtectedConstructorInAbstractClassVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class MakeProtectedFix extends InspectionGadgetsFix {
        public String getName() {
            return "Make 'protected'";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            final PsiElement constructorIdentifier = descriptor.getPsiElement();
            try {
                final PsiMethod constructor = (PsiMethod) constructorIdentifier.getParent();
                final PsiModifierList modifiers = constructor.getModifierList();
                modifiers.setModifierProperty(PsiModifier.PUBLIC, false);
                modifiers.setModifierProperty(PsiModifier.PRIVATE, false);
                modifiers.setModifierProperty(PsiModifier.PROTECTED, true);
            } catch (IncorrectOperationException e) {
                s_logger.error(e);
            }
        }
    }

    private class NonProtectedConstructorInAbstractClassVisitor extends BaseInspectionVisitor {
        private NonProtectedConstructorInAbstractClassVisitor(BaseInspection inspection,
                                                              InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            //no call to super, so we don't drill into anonymous classes
            if (!method.isConstructor()) {
                return;
            }
            if (method.hasModifierProperty(PsiModifier.PROTECTED)
                    || method.hasModifierProperty(PsiModifier.PRIVATE)) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (m_ignoreNonPublicClasses && !containingClass.hasModifierProperty(PsiModifier.PUBLIC)) {
                return;
            }
            if (!containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            if (containingClass.isEnum()) {
                return;
            }
            registerMethodError(method);
        }

    }


}
