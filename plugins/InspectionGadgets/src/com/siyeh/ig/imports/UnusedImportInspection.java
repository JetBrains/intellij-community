package com.siyeh.ig.imports;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.*;
import com.intellij.openapi.project.Project;
import com.siyeh.ig.*;

public class UnusedImportInspection extends ClassInspection {
    private final UnusedImportFix fix = new UnusedImportFix();

    public String getDisplayName() {
        return "Unused import";
    }

    public String getGroupDisplayName() {
        return GroupNames.IMPORTS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Unused import '#ref' #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class UnusedImportFix extends InspectionGadgetsFix {
        public String getName() {
            return "Delete unused import";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            final PsiElement importStatement = descriptor.getPsiElement();
            deleteElement(importStatement);
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new UnusedImportVisitor(this, inspectionManager, onTheFly);
    }

    private static class UnusedImportVisitor extends BaseInspectionVisitor {
        private UnusedImportVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            if (!(aClass.getParent() instanceof PsiJavaFile)) {
                return;
            }
            final PsiJavaFile file = (PsiJavaFile) aClass.getParent();
            if (!file.getClasses()[0].equals(aClass)) {
                return;
            }
            final PsiImportList importList = file.getImportList();
            final PsiImportStatement[] importStatements = importList.getImportStatements();
            for (int i = 0; i < importStatements.length; i++) {
                final PsiImportStatement importStatement = importStatements[i];
                if (!isNecessaryImport(importStatement, file.getClasses())) {
                    registerError(importStatement);
                }
            }
        }

        private static boolean isNecessaryImport(PsiImportStatement importStatement, PsiClass[] classes) {
            final ImportIsUsedVisitor visitor = new ImportIsUsedVisitor(importStatement);
            for (int i = 0; i < classes.length; i++) {
                classes[i].accept(visitor);
                final PsiClass[] innerClasses = classes[i].getInnerClasses();
                for (int j = 0; j < innerClasses.length; j++) {
                    innerClasses[j].accept(visitor);
                }
            }
            return visitor.isUsed();
        }

    }
}
