package com.siyeh.ig.imports;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.DeleteImportFix;
import com.siyeh.ig.psiutils.ImportUtils;
import org.jetbrains.annotations.NotNull;

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

    public BaseInspectionVisitor buildVisitor() {
        return new JavaLangImportVisitor();
    }

    private static class JavaLangImportVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
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
            for(PsiImportStatement importStatement : importStatements){
                checkImportStatment(importStatement, file);
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
