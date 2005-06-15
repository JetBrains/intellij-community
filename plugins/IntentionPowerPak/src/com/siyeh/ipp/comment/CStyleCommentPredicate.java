package com.siyeh.ipp.comment;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;

class CStyleCommentPredicate implements PsiElementPredicate{

    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiComment)){
            return false;
        }
        final PsiComment comment = (PsiComment) element;
        final IElementType type = comment.getTokenType();
        if(!JavaTokenType.C_STYLE_COMMENT.equals(type)){
            return false;
        }
        final PsiElement sibling = comment.getNextSibling();
        if(!(sibling instanceof PsiWhiteSpace))
        {
            return false;
        }
        return (sibling.getText()).indexOf('\n') >=0;
    }
}
