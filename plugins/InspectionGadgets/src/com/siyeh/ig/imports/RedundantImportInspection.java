package com.siyeh.ig.imports;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.*;
import com.intellij.openapi.project.Project;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ImportUtils;

import java.util.HashSet;
import java.util.Set;

public class RedundantImportInspection extends ClassInspection {
    private final RedundantImportFix fix = new RedundantImportFix();

    public String getDisplayName() {
        return "Redundant import";
    }

    public String getGroupDisplayName() {
        return GroupNames.IMPORTS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Redundant import '#ref' #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class RedundantImportFix extends InspectionGadgetsFix {
        public String getName() {
            return "Delete redundant import";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            final PsiElement importStatement = descriptor.getPsiElement();
            deleteElement(importStatement);
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new RedundantImportVisitor(this, inspectionManager, onTheFly);
    }

    private static class RedundantImportVisitor extends BaseInspectionVisitor {
        private RedundantImportVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            // no call to super, so it doesn't drill down
            if (!(aClass.getParent() instanceof PsiJavaFile)) {
                return;
            }
            final PsiJavaFile file = (PsiJavaFile) aClass.getParent();
            if (!file.getClasses()[0].equals(aClass)) {
                return;
            }
            final PsiImportList importList = file.getImportList();
            final PsiImportStatement[] importStatements = importList.getImportStatements();
            final Set imports = new HashSet(importStatements.length);
            for (int i = 0; i < importStatements.length; i++) {
                final PsiImportStatement importStatement = importStatements[i];
                final String text = importStatement.getQualifiedName();
                if (text == null) {
                    return;
                }
                if (imports.contains(text)) {
                    registerError(importStatement);
                }
                if (!importStatement.isOnDemand()) {
                    final int classNameIndex = text.lastIndexOf((int) '.');
                    if (classNameIndex < 0) {
                        return;
                    }
                    final String parentName = text.substring(0, classNameIndex);
                    if (imports.contains(parentName)) {
                        if (!ImportUtils.hasOnDemandImportConflict(text, file)) {
                            registerError(importStatement);
                        }
                    }
                }
                imports.add(text);
            }
        }

    }
}
