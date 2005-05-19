package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.siyeh.ig.InspectionGadgetsFix;

public class MoveAnonymousToInnerClassFix extends InspectionGadgetsFix {
    public String getName() {
        return "Make named inner class";
    }

    public void doFix(Project project, ProblemDescriptor descriptor) {
        final PsiElement nameElement = descriptor.getPsiElement();
        final PsiAnonymousClass aClass = (PsiAnonymousClass) nameElement.getParent();
        final RefactoringActionHandlerFactory factory =
                RefactoringActionHandlerFactory.getInstance();
        final RefactoringActionHandler anonymousToInner = factory.createAnonymousToInnerHandler();
        anonymousToInner.invoke(project, new PsiElement[]{aClass}, null);
    }
}
