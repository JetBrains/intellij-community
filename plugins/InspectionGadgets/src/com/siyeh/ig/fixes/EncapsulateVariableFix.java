package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.siyeh.ig.InspectionGadgetsFix;

public class EncapsulateVariableFix extends InspectionGadgetsFix {

    public String getName() {
        return "Encapsulate variable";
    }

    public void applyFix(Project project, ProblemDescriptor problemDescriptor) {
        final PsiElement nameElement = problemDescriptor.getPsiElement();
        final PsiField field = (PsiField) nameElement.getParent();
        final RefactoringActionHandlerFactory factory =
                RefactoringActionHandlerFactory.getInstance();
        final RefactoringActionHandler renameHandler =
                factory.createEncapsulateFieldsHandler();
        renameHandler.invoke(project, new PsiElement[]{field}, null);
    }
}
