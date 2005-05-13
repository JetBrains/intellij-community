package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringFactory;
import com.intellij.refactoring.RenameRefactoring;
import com.siyeh.ig.InspectionGadgetsFix;

public class RenameFix extends InspectionGadgetsFix {
    private final String m_targetName;

    public RenameFix() {
        super();
        m_targetName = null;
    }

    public RenameFix(String targetName) {
        super();
        m_targetName = targetName;
    }

    public String getName() {
        if (m_targetName == null) {
            return "Rename";
        } else {
            return "Rename to '" + m_targetName + '\'';
        }
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
        if(isQuickFixOnReadOnlyFile(descriptor)) return;
        final PsiElement nameIdentifier = descriptor.getPsiElement();
        final PsiElement elementToRename = nameIdentifier.getParent();
        if (m_targetName == null) {
            final RefactoringActionHandlerFactory factory =
                    RefactoringActionHandlerFactory.getInstance();
            final RefactoringActionHandler renameHandler = factory.createRenameHandler();
            renameHandler.invoke(project, new PsiElement[]{elementToRename}, null);
        } else {
            final RefactoringFactory factory = RefactoringFactory.getInstance(project);
            final RenameRefactoring renameRefactoring = factory.createRename(elementToRename, m_targetName);
            renameRefactoring.run();
        }
    }
}
