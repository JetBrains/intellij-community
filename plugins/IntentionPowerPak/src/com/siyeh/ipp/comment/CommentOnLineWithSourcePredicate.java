package com.siyeh.ipp.comment;

import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.javadoc.PsiDocComment;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.TreeUtil;

class CommentOnLineWithSourcePredicate implements PsiElementPredicate{

    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiComment)){
            return false;
        }
        if(element instanceof PsiDocComment){
            return false;
        }
        final PsiElement prevSibling = TreeUtil.getPrevLeaf(element);

        if(!(prevSibling instanceof PsiWhiteSpace))
        {
            return true;
        }
        final String prevSiblingText = prevSibling.getText();
        if(prevSiblingText.indexOf((int) '\n')<0 &&
                prevSiblingText.indexOf((int) '\r') < 0)
        {
            return true;
        }
        final PsiElement nextSibling = TreeUtil.getNextLeaf(element);
        if(!(nextSibling instanceof PsiWhiteSpace))
        {
            return true;
        }
        final String nextSiblingText = nextSibling.getText();
        return nextSiblingText.indexOf((int) '\n')<0 &&
                nextSiblingText.indexOf((int) '\r') < 0;
    }
}
