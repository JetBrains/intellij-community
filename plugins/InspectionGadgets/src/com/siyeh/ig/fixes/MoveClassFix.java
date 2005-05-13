package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.siyeh.ig.InspectionGadgetsFix;

public class MoveClassFix extends InspectionGadgetsFix {
    public String getName() {
        return "Move class";
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
        if(isQuickFixOnReadOnlyFile(descriptor)) return;
        final PsiElement nameElement = descriptor.getPsiElement();
        final PsiClass aClass = (PsiClass) nameElement.getParent();
        final RefactoringActionHandlerFactory factory =
                RefactoringActionHandlerFactory.getInstance();
        final RefactoringActionHandler renameHandler = factory.createMoveHandler();
        renameHandler.invoke(project, new PsiElement[]{aClass}, null);
    }
}
