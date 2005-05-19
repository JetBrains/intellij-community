package com.siyeh.ig.fixes;

import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;

public  class DeleteImportFix extends InspectionGadgetsFix {
    public String getName() {
        return "Delete unnecessary import";
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
                                                                     throws IncorrectOperationException{
        final PsiElement importStatement = descriptor.getPsiElement();
        deleteElement(importStatement);
    }
}
