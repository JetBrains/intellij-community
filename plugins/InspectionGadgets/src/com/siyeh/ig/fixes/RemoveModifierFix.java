package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
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
        if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(new VirtualFile[]{descriptor.getPsiElement().getContainingFile().getVirtualFile()}).hasReadonlyFiles()) return;
        final PsiElement modifierElement = descriptor.getPsiElement();
        deleteElement(modifierElement);
    }
}
