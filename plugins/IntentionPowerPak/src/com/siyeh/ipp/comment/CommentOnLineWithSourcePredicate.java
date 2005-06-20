package com.siyeh.ipp.comment;

import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.siyeh.ipp.base.PsiElementPredicate;

class CommentOnLineWithSourcePredicate implements PsiElementPredicate{

    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiComment)){
            return false;
        }
        final PsiElement prevSibling = element.getPrevSibling();

        if(!(prevSibling instanceof PsiWhiteSpace))
        {
            return true;
        }
        if(prevSibling.getText().indexOf((int) '\n')<0)
        {
            return true;
        }
        final PsiElement nextSibling = element.getNextSibling();
        if(!(nextSibling instanceof PsiWhiteSpace))
        {
            return true;
        }
        return nextSibling.getText().indexOf((int) '\n')<0;
    }
}
