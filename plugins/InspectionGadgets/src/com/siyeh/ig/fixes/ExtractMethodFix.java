package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.siyeh.ig.InspectionGadgetsFix;

public class ExtractMethodFix extends InspectionGadgetsFix {

    public String getName() {
        return "Extract method";
    }

    public void applyFix(Project project, ProblemDescriptor problemDescriptor) {
        final PsiExpression expression = (PsiExpression) problemDescriptor.getPsiElement();
        final RefactoringActionHandlerFactory factory =
                RefactoringActionHandlerFactory.getInstance();
        final RefactoringActionHandler inlineHandler = factory.createExtractMethodHandler();
        inlineHandler.invoke(project, new PsiElement[]{expression}, null);
    }
}
