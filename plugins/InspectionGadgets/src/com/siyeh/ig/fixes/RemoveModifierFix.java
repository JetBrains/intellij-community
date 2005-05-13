package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.InspectionGadgetsFix;

public class RemoveModifierFix extends InspectionGadgetsFix {
    private final PsiElement modifier;

    public RemoveModifierFix(PsiElement modifier) {
        super();
        this.modifier = modifier;
    }

    public String getName() {
        return "Remove '" + modifier.getText() + "' modifier";
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
        if(isQuickFixOnReadOnlyFile(descriptor)) return;
        final PsiElement modifierElement = descriptor.getPsiElement();
        deleteElement(modifierElement);
    }
}
