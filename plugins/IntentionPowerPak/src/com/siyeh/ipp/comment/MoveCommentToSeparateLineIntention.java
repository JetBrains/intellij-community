package com.siyeh.ipp.comment;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class MoveCommentToSeparateLineIntention extends Intention{
    @NotNull
    protected PsiElementPredicate getElementPredicate(){
        return new CommentOnLineWithSourcePredicate();
    }

    public String getText(){
        return "Move comment to separate line";
    }

    public String getFamilyName(){
        return "Move Comment To Separate Line";
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiComment selectedComment = (PsiComment) element;
        final PsiWhiteSpace whiteSpace;
        PsiElement elementToCheck = selectedComment;
        PsiElement level = selectedComment;
        while(true){
            elementToCheck = elementToCheck.getPrevSibling();
            if(elementToCheck == null)
            {
                elementToCheck = level.getParent();
                level = elementToCheck;
            }
            if(elementToCheck == null)
            {
               return;
            }
            if(isLineBreakWhiteSpace(elementToCheck)){
                whiteSpace = (PsiWhiteSpace) elementToCheck;
                break;
            }
        }
        final PsiElement copyWhiteSpace = whiteSpace.copy();
        final PsiElement parent = whiteSpace.getParent();
        assert parent!=null;
        final PsiManager manager = selectedComment.getManager();
        final PsiElementFactory factory = manager.getElementFactory();
        final String commentText = selectedComment.getText();
        final PsiComment newComment =
                factory.createCommentFromText(commentText, parent);
        final PsiElement insertedComment = parent.addBefore(newComment, whiteSpace);
        parent.addBefore(copyWhiteSpace, insertedComment);

        selectedComment.delete();
        final CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
        codeStyleManager.reformat(parent);
    }

    private static boolean isLineBreakWhiteSpace(PsiElement element){
        if(!(element instanceof PsiWhiteSpace))
        {
            return false;
        }
        final String text = element.getText();
        return containsLineBreak(text);
    }

    private static boolean containsLineBreak(String text){
        return text.indexOf((int) '\n')>=0 || text.indexOf((int) '\r') >= 0;
    }
}
