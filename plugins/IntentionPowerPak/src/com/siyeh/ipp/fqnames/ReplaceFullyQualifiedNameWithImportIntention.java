package com.siyeh.ipp.fqnames;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ReplaceFullyQualifiedNameWithImportIntention extends Intention{
    public String getText(){
        return "Replace qualified name with import";
    }

    public String getFamilyName(){
        return "Replace Qualified Name With Import";
    }

    @NotNull
    public PsiElementPredicate getElementPredicate(){
        return new FullyQualifiedNamePredicate();
    }

    public void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException{
        PsiJavaCodeReferenceElement reference =
                (PsiJavaCodeReferenceElement) element;
        while(reference != null &&
                reference.getParent() instanceof PsiJavaCodeReferenceElement){
            reference = (PsiJavaCodeReferenceElement) reference.getParent();
        }

        final PsiManager mgr = element.getManager();
        final CodeStyleManager styleManager = mgr.getCodeStyleManager();
        styleManager.shortenClassReferences(reference);
    }
}
