package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.siyeh.ig.InspectionGadgetsFix;

public class EncapsulateVariableFix extends InspectionGadgetsFix {

    public String getName() {
        return "Encapsulate variable";
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
        if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(new VirtualFile[]{descriptor.getPsiElement().getContainingFile().getVirtualFile()}).hasReadonlyFiles()) return;
        final PsiElement nameElement = descriptor.getPsiElement();
        final PsiField field = (PsiField) nameElement.getParent();
        final RefactoringActionHandlerFactory factory =
                RefactoringActionHandlerFactory.getInstance();
        final RefactoringActionHandler renameHandler =
                factory.createEncapsulateFieldsHandler();
        renameHandler.invoke(project, new PsiElement[]{field}, null);
    }
}
