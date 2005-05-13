package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.siyeh.ig.InspectionGadgetsFix;

public class InlineVariableFix extends InspectionGadgetsFix {

    public String getName() {
        return "Inline variable";
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
        if(isQuickFixOnReadOnlyFile(descriptor)) return;
        final PsiElement nameElement = descriptor.getPsiElement();
        final PsiLocalVariable variable = (PsiLocalVariable) nameElement.getParent();
        final RefactoringActionHandlerFactory factory =
                RefactoringActionHandlerFactory.getInstance();
        final RefactoringActionHandler inlineHandler = factory.createInlineHandler();
        inlineHandler.invoke(project, new PsiElement[]{variable}, null);
    }
}
