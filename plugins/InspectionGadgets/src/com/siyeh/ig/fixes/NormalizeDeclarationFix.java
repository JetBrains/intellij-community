package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiVariable;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.InspectionGadgetsFix;

public class NormalizeDeclarationFix extends InspectionGadgetsFix {
    public String getName() {
        return "Split into multiple declarations";
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
                                                                     throws IncorrectOperationException{
        final PsiElement variableNameElement = descriptor.getPsiElement();
        final PsiVariable var = (PsiVariable) variableNameElement.getParent();
            var.normalizeDeclaration();
    }
}
