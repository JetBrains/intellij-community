package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.*;

public class TypeParameterExtendsObjectInspection extends ClassInspection {
    private final ExtendsObjectFix fix = new ExtendsObjectFix();

    public String getDisplayName() {
        return "Type parameter explicitly extends java.lang.Object";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Type parameter '#ref' explicitly extends java.lang.Object #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class ExtendsObjectFix extends InspectionGadgetsFix {
        public String getName() {
            return "Remove redundant 'extends Object'";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            final PsiElement extendClassIdentifier = descriptor.getPsiElement();
            final PsiTypeParameter element = (PsiTypeParameter) extendClassIdentifier.getParent();
            final PsiReferenceList extendsList = element.getExtendsList();
            final PsiJavaCodeReferenceElement[] elements = extendsList.getReferenceElements();
            for (int i = 0; i < elements.length; i++) {
                deleteElement(elements[i]);
            }
        }

    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ExtendsObjectVisitor(this, inspectionManager, onTheFly);
    }

    private static class ExtendsObjectVisitor extends BaseInspectionVisitor {

        private ExtendsObjectVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitTypeParameter(PsiTypeParameter parameter) {
            super.visitTypeParameter(parameter);
            final PsiIdentifier nameIdentifier = parameter.getNameIdentifier();
            final PsiReferenceList extendsList = parameter.getExtendsList();
            if (extendsList != null) {
                final PsiJavaCodeReferenceElement[] elements = extendsList.getReferenceElements();
                for (int i = 0; i < elements.length; i++) {
                    final PsiJavaCodeReferenceElement element = elements[i];
                    final String text = element.getText();
                    if ("Object".equals(text) || "java.lang.Object".equals(text)) {
                        registerError(nameIdentifier);
                    }
                }
            }
        }

    }
}
