package com.siyeh.ig.imports;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.DeleteImportFix;

public class SamePackageImportInspection extends ClassInspection {
    private final DeleteImportFix fix = new DeleteImportFix();

    public String getDisplayName() {
        return "Import from same package";
    }

    public String getGroupDisplayName() {
        return GroupNames.IMPORTS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Unnecessary import from same package '#ref' #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SamePackageImportVisitor(this, inspectionManager, onTheFly);
    }

    private static class SamePackageImportVisitor extends BaseInspectionVisitor {
        private SamePackageImportVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
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
            final String packageName = file.getPackageName();
            final PsiImportList importList = file.getImportList();
            final PsiImportStatement[] importStatements = importList.getImportStatements();
            for (int i = 0; i < importStatements.length; i++) {
                final PsiImportStatement importStatement = importStatements[i];
                final PsiJavaCodeReferenceElement reference = importStatement.getImportReference();
                if (reference != null) {
                    final String text = importStatement.getQualifiedName();
                    if (importStatement.isOnDemand()) {
                        if (packageName.equals(text)) {
                            registerError(importStatement);
                        }
                    } else {
                        final int classNameIndex = text.lastIndexOf((int) '.');
                        final String parentName = classNameIndex < 0 ? "" : text.substring(0, classNameIndex);
                        if (packageName.equals(parentName)) {
                            registerError(importStatement);
                        }
                    }
                }
            }
        }

    }
}
