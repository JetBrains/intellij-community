package com.siyeh.ig.fixes;

import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiElement;

public  class DeleteImportFix extends InspectionGadgetsFix {
    public String getName() {
        return "Delete unnecessary import";
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
        if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
        final PsiElement importStatement = descriptor.getPsiElement();
        deleteElement(importStatement);
    }
}
