package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.*;

public class ExtendsObjectInspection extends ClassInspection {
    private final ExtendsObjectFix fix = new ExtendsObjectFix();

    public String getDisplayName() {
        return "Class explicitly extends java.lang.Object";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Class '#ref' explicitly extends java.lang.Object #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class ExtendsObjectFix extends InspectionGadgetsFix {
        public String getName() {
            return "Remove redundant 'extends Object'";
        }

        public void applyFix(Project project, ProblemDescriptor problemDescriptor) {
            final PsiElement extendClassIdentifier = problemDescriptor.getPsiElement();
            final PsiClass element = (PsiClass) extendClassIdentifier.getParent();
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

        public void visitClass(PsiClass aClass) {
            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            final PsiReferenceList extendsList = aClass.getExtendsList();
            if (extendsList != null) {
                final PsiJavaCodeReferenceElement[] elements = extendsList.getReferenceElements();
                for (int i = 0; i < elements.length; i++) {
                    final PsiJavaCodeReferenceElement element = elements[i];
                    final PsiElement referent = element.resolve();
                    if(referent instanceof PsiClass)
                    {
                        final String className = ((PsiClass) referent).getQualifiedName();
                        if ( "java.lang.Object".equals(className)) {
                            registerClassError(aClass);
                        }
                    }
                }

            }
        }

    }
}
