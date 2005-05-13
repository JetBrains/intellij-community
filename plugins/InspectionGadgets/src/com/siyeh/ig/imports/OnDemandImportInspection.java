package com.siyeh.ig.imports;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

public class OnDemandImportInspection extends ClassInspection {

    public String getDisplayName() {
        return "* import";
    }

    public String getGroupDisplayName() {
        return GroupNames.IMPORTS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Importing package #ref.*  #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new PackageImportVisitor(this, inspectionManager, onTheFly);
    }

    private static class PackageImportVisitor extends BaseInspectionVisitor {
        private PackageImportVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so it doesn't drill down
            if (!(aClass.getParent() instanceof PsiJavaFile)) {
                return;
            }
            final PsiJavaFile file = (PsiJavaFile) aClass.getParent();
            if (!file.getClasses()[0].equals(aClass)) {
                return;
            }
            final PsiImportList importList = file.getImportList();
            if (importList != null) {
                final PsiImportStatement[] importStatements = importList.getImportStatements();
                for(final PsiImportStatement importStatement : importStatements){
                    final PsiJavaCodeReferenceElement reference = importStatement.getImportReference();

                    if(importStatement.isOnDemand()){
                        if(reference != null){
                            registerError(reference);
                        }
                    }
                }
            }
        }

    }
}
