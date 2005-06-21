package com.siyeh.ig.imports;

import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import org.jetbrains.annotations.NotNull;

public class SingleClassImportInspection extends ClassInspection {

    public String getDisplayName() {

        return "Single class import";
    }

    public String getGroupDisplayName() {
        return GroupNames.IMPORTS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Single class import '#ref' #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new PackageImportVisitor();
    }

    private static class PackageImportVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so it doesn't drill down
            if (!(aClass.getParent() instanceof PsiJavaFile)) {
                return;
            }
            final PsiJavaFile file = (PsiJavaFile) aClass.getParent();
            if(file == null)
            {
                return;
            }
            if (!file.getClasses()[0].equals(aClass)) {
                return;
            }
            final PsiImportList importList = file.getImportList();
            final PsiImportStatement[] importStatements = importList.getImportStatements();
            for(final PsiImportStatement importStatement : importStatements){
                if(!importStatement.isOnDemand()){
                    registerError(importStatement);
                }
            }
        }

    }
}
