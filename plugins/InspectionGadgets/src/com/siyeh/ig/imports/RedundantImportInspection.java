package com.siyeh.ig.imports;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.DeleteImportFix;
import com.siyeh.ig.psiutils.ImportUtils;

import java.util.HashSet;
import java.util.Set;

public class RedundantImportInspection extends ClassInspection {
    private final DeleteImportFix fix = new DeleteImportFix();

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
            final Set<String> imports = new HashSet<String>(importStatements.length);
            for(final PsiImportStatement importStatement : importStatements){
                final String text = importStatement.getQualifiedName();
                if(text == null){
                    return;
                }
                if(imports.contains(text)){
                    registerError(importStatement);
                }
                if(!importStatement.isOnDemand()){
                    final int classNameIndex = text.lastIndexOf((int) '.');
                    if(classNameIndex < 0){
                        return;
                    }
                    final String parentName = text.substring(0, classNameIndex);
                    if(imports.contains(parentName)){
                        if(!ImportUtils.hasOnDemandImportConflict(text, file)){
                            registerError(importStatement);
                        }
                    }
                }
                imports.add(text);
            }
        }

    }
}
