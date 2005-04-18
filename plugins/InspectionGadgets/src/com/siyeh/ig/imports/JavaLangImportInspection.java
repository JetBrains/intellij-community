package com.siyeh.ig.imports;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.DeleteImportFix;
import com.siyeh.ig.psiutils.ImportUtils;

public class JavaLangImportInspection extends ClassInspection {
    private final DeleteImportFix fix = new DeleteImportFix();

    public String getDisplayName() {
        return "java.lang import";
    }

    public String getGroupDisplayName() {
        return GroupNames.IMPORTS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Unnecessary import from package java.lang #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new JavaLangImportVisitor(this, inspectionManager, onTheFly);
    }

    private static class JavaLangImportVisitor extends BaseInspectionVisitor {
        private JavaLangImportVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass) {
            // no call to super, so it doesn't drill down
            if (!(aClass.getParent() instanceof PsiJavaFile)) {
                return;
            }
            final PsiJavaFile file = (PsiJavaFile) aClass.getContainingFile();
            if (!file.getClasses()[0].equals(aClass)) {
                return;
            }
            final PsiImportList importList = file.getImportList();
            final PsiImportStatement[] importStatements = importList.getImportStatements();
            for (int i = 0; i < importStatements.length; i++) {
                checkImportStatment(importStatements[i], file);
            }
        }

        private void checkImportStatment( PsiImportStatement importStatement,
                                         PsiJavaFile file){
            final PsiJavaCodeReferenceElement reference = importStatement.getImportReference();
            if (reference != null) {
                final String text = importStatement.getQualifiedName();
                if (text != null) {
                    if (importStatement.isOnDemand()) {
                        if ("java.lang".equals(text)) {
                            registerError(importStatement);
                        }
                    } else {
                        final int classNameIndex = text.lastIndexOf((int) '.');
                        if (classNameIndex < 0) {
                            return;
                        }
                        final String parentName = text.substring(0, classNameIndex);
                        if ("java.lang".equals(parentName)) {
                            if (!ImportUtils.hasOnDemandImportConflict(text, file)) {
                                registerError(importStatement);
                            }
                        }
                    }
                }
            }
        }

    }
}
