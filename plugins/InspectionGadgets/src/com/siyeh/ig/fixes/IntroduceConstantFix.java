package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.siyeh.ig.InspectionGadgetsFix;

public class IntroduceConstantFix extends InspectionGadgetsFix {
    public String getName() {
        return "Introduce constant";
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
        if(isQuickFixOnReadOnlyFile(descriptor)) return;
        final RefactoringActionHandlerFactory factory = RefactoringActionHandlerFactory.getInstance();
        final RefactoringActionHandler introduceHandler = factory.createIntroduceConstantHandler();
        final PsiElement constant = descriptor.getPsiElement();
        introduceHandler.invoke(project, new PsiElement[]{constant}, null);
    }
}
