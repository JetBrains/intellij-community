package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.siyeh.ig.InspectionGadgetsFix;

public class InlineCallFix extends InspectionGadgetsFix {

    public String getName() {
        return "Inline call";
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
        if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
        final PsiElement nameElement = descriptor.getPsiElement();
        final PsiReferenceExpression methodExpression =
                (PsiReferenceExpression) nameElement.getParent();
        final PsiMethodCallExpression methodCallExpression =
                (PsiMethodCallExpression) methodExpression.getParent();
        final RefactoringActionHandlerFactory factory =
                RefactoringActionHandlerFactory.getInstance();
        final RefactoringActionHandler inlineHandler = factory.createInlineHandler();
        inlineHandler.invoke(project, new PsiElement[]{methodCallExpression}, null);
    }
}
