package com.siyeh.ig.fixes;

import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiVariable;
import com.intellij.util.IncorrectOperationException;

public class NormalizeDeclarationFix extends InspectionGadgetsFix {
    public String getName() {
        return "Split into multiple declarations";
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
        if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(new VirtualFile[]{descriptor.getPsiElement().getContainingFile().getVirtualFile()}).hasReadonlyFiles()) return;
        final PsiElement variableNameElement = descriptor.getPsiElement();
        final PsiVariable var = (PsiVariable) variableNameElement.getParent();
        try {
            var.normalizeDeclaration();
        } catch (IncorrectOperationException e) {
            final Class aClass = getClass();
            final String className = aClass.getName();
            final Logger logger = Logger.getInstance(className);
            logger.error(e);
        }
    }
}
