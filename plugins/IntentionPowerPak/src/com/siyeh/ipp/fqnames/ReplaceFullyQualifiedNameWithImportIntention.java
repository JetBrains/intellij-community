package com.siyeh.ipp.fqnames;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.Intention;
import com.siyeh.ipp.PsiElementPredicate;

public class ReplaceFullyQualifiedNameWithImportIntention extends Intention {
    public ReplaceFullyQualifiedNameWithImportIntention(Project project) {
        super(project);
    }

    public String getText() {
        return "Replace qualified name with import";
    }

    public String getFamilyName() {
        return "Replace Qualified Name With Import";
    }

    public PsiElementPredicate getElementPredicate() {
        return new FullyQualifiedNamePredicate();
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        final PsiManager mgr = PsiManager.getInstance(project);
        PsiJavaCodeReferenceElement element =
                (PsiJavaCodeReferenceElement) findMatchingElement(file, editor);
        while (element.getParent() instanceof PsiJavaCodeReferenceElement) {
            element = (PsiJavaCodeReferenceElement) element.getParent();
        }

        final CodeStyleManager styleManager = mgr.getCodeStyleManager();
        styleManager.shortenClassReferences(element);
    }
}
