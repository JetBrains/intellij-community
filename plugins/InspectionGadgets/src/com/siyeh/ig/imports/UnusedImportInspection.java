package com.siyeh.ig.imports;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.DeleteImportFix;
import org.jetbrains.annotations.NotNull;

public class UnusedImportInspection extends ClassInspection {
    private final DeleteImportFix fix = new DeleteImportFix();

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

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new UnusedImportVisitor(this, inspectionManager, onTheFly);
    }

    private static class UnusedImportVisitor extends BaseInspectionVisitor {
        private UnusedImportVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(@NotNull PsiClass aClass) {
            if (!(aClass.getParent() instanceof PsiJavaFile)) {
                return;
            }
            final PsiJavaFile file = (PsiJavaFile) aClass.getParent();
            if (!file.getClasses()[0].equals(aClass)) {
                return;
            }
            final PsiImportList importList = file.getImportList();
            final PsiImportStatement[] importStatements = importList.getImportStatements();
            for(final PsiImportStatement importStatement : importStatements){
                if(!isNecessaryImport(importStatement, file.getClasses())){
                    registerError(importStatement);
                }
            }
        }

        private static boolean isNecessaryImport(PsiImportStatement importStatement, PsiClass[] classes) {
            final ImportIsUsedVisitor visitor = new ImportIsUsedVisitor(importStatement);
            for(PsiClass aClasses : classes){
                aClasses.accept(visitor);
                final PsiClass[] innerClasses = aClasses.getInnerClasses();
                for(PsiClass innerClass : innerClasses){
                    innerClass.accept(visitor);
                }
            }
            return visitor.isUsed();
        }

    }
}
