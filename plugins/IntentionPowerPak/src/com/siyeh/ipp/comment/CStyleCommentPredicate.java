package com.siyeh.ipp.comment;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.TreeUtil;

class CStyleCommentPredicate implements PsiElementPredicate{

    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiComment)) {
            return false;
        }
        if(element instanceof PsiDocComment){
            return false;
        }
        final PsiComment comment = (PsiComment) element;
        final IElementType type = comment.getTokenType();
        if(!JavaTokenType.C_STYLE_COMMENT.equals(type)){
            return false;
        }
        final PsiElement sibling = TreeUtil.getNextLeaf(comment);
        if(!(sibling instanceof PsiWhiteSpace))
        {
            return false;
        }
        final String whitespaceText = sibling.getText();
        return whitespaceText.indexOf((int) '\n') >=0 ||
                whitespaceText.indexOf((int) '\r') >= 0;
    }
}
