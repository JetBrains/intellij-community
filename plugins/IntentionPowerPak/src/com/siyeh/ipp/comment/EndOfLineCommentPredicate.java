package com.siyeh.ipp.comment;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;

class EndOfLineCommentPredicate implements PsiElementPredicate{

    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiComment)){
            return false;
        }
        final PsiComment comment = (PsiComment) element;
        final IElementType type = comment.getTokenType();
        return JavaTokenType.END_OF_LINE_COMMENT.equals(type);
    }
}
