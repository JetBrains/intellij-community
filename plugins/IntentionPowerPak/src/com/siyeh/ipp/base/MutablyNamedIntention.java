package com.siyeh.ipp.base;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

public abstract class MutablyNamedIntention extends Intention{
    private String m_text = null;

    protected abstract String getTextForElement(PsiElement element);

    public String getText(){
        return m_text;
    }

    public boolean isAvailable(Project project, Editor editor, PsiFile file){
        final PsiElement element = findMatchingElement(file, editor);
        if(element != null){
            m_text = getTextForElement(element);
        }
        return element != null;
    }
}
