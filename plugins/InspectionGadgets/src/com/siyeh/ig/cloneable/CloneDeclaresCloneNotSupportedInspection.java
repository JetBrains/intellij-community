package com.siyeh.ig.cloneable;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;

public class CloneDeclaresCloneNotSupportedInspection extends MethodInspection {
    private final CloneDeclaresCloneNotSupportedInspectionFix fix = new CloneDeclaresCloneNotSupportedInspectionFix();

    public String getID(){
        return "CloneDoesntDeclareCloneNotSupportedException";
    }
    public String getDisplayName() {
        return "'clone()' doesn't declare CloneNotSupportedException";
    }

    public String getGroupDisplayName() {
        return GroupNames.CLONEABLE_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref() doesn't declare CloneNotSupportedException #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class CloneDeclaresCloneNotSupportedInspectionFix extends InspectionGadgetsFix {
        public String getName() {
            return "Declare CloneNotSupportedException";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            try {
                final PsiElement methodNameIdentifier = descriptor.getPsiElement();
                final PsiMethod method = (PsiMethod) methodNameIdentifier.getParent();
                PsiUtil.addException(method, "java.lang.CloneNotSupportedException");
            } catch (IncorrectOperationException e) {
                final Class thisClass = getClass();
                final String className = thisClass.getName();
                final Logger logger = Logger.getInstance(className);
                logger.error(e);
            }
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new CloneDeclaresCloneNotSupportedExceptionVisitor(this, inspectionManager, onTheFly);
    }

    private static class CloneDeclaresCloneNotSupportedExceptionVisitor extends BaseInspectionVisitor {
        private CloneDeclaresCloneNotSupportedExceptionVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            //note: no call to super;
            final String methodName = method.getName();
            if (!"clone".equals(methodName)) {
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if(parameterList == null)
            {
                return;
            }
            final PsiParameter[] parameters = parameterList.getParameters();
            if (parameters == null ||parameters.length != 0) {
                return;
            }
            if (method.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            final PsiReferenceList throwsList = method.getThrowsList();
            if (throwsList == null) {
                registerMethodError(method);
                return;
            }
            final PsiJavaCodeReferenceElement[] referenceElements = throwsList.getReferenceElements();
            for (int i = 0; i < referenceElements.length; i++) {
                final PsiJavaCodeReferenceElement referenceElement = referenceElements[i];
                final PsiElement referencedElement = referenceElement.resolve();
                if (referencedElement != null && referencedElement instanceof PsiClass) {
                    final PsiClass aClass = (PsiClass) referencedElement;
                    final String className = aClass.getQualifiedName();
                    if ("java.lang.CloneNotSupportedException".equals(className)) {
                        return;
                    }
                }
            }

            registerMethodError(method);
        }

    }

}
