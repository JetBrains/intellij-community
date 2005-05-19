package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
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

    public void doFix(Project project, ProblemDescriptor descriptor)
                                                                     throws IncorrectOperationException{
        final PsiElement modifierElement = descriptor.getPsiElement();
        deleteElement(modifierElement);
    }
}
