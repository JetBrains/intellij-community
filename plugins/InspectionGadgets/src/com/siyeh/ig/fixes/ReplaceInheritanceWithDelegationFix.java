package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.siyeh.ig.InspectionGadgetsFix;

public class ReplaceInheritanceWithDelegationFix extends InspectionGadgetsFix {
    public String getName() {
        return "Replace inheritance with delegation";
    }

    public void doFix(Project project, ProblemDescriptor descriptor) {
        final PsiElement nameElement = descriptor.getPsiElement();
        final PsiClass aClass = (PsiClass) nameElement.getParent();
        final RefactoringActionHandlerFactory factory =
                RefactoringActionHandlerFactory.getInstance();
        final RefactoringActionHandler anonymousToInner = factory.createInheritanceToDelegationHandler();
        anonymousToInner.invoke(project, new PsiElement[]{aClass}, null);
    }
}
