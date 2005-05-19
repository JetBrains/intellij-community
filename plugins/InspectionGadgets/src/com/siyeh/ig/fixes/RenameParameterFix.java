package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringFactory;
import com.intellij.refactoring.RenameRefactoring;
import com.siyeh.ig.InspectionGadgetsFix;

public class RenameParameterFix extends InspectionGadgetsFix {
    private final String m_targetName;


    public RenameParameterFix(String targetName) {
        super();
        m_targetName = targetName;
    }

    public String getName() {
        return "Rename to '" + m_targetName + '\'';
    }

    public void doFix(Project project, ProblemDescriptor descriptor) {
        final PsiElement nameIdentifier = descriptor.getPsiElement();
        final PsiElement elementToRename = nameIdentifier.getParent();
        final RefactoringFactory factory =
                RefactoringFactory.getInstance(project);
        final RenameRefactoring renameRefactoring =
                factory.createRename(elementToRename, m_targetName);
        renameRefactoring.setSearchInComments(false);
        renameRefactoring.setSearchInNonJavaFiles(false);
        renameRefactoring.run();
    }
}
