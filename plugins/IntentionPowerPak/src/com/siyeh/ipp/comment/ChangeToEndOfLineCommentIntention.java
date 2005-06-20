package com.siyeh.ipp.comment;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ChangeToEndOfLineCommentIntention extends Intention{
    @NotNull
    protected PsiElementPredicate getElementPredicate(){
        return new CStyleCommentPredicate();
    }

    public String getText(){
        return "Replace with end-of-line comment";
    }

    public String getFamilyName(){
        return "Replace With End Of Line Comment";
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiComment comment = (PsiComment) element;
        final PsiManager manager = comment.getManager();
        final CodeStyleManager codeStyleManager = manager.getCodeStyleManager();
        final PsiElement parent = comment.getParent();
        assert parent != null;
        final PsiElementFactory factory = manager.getElementFactory();
        final String commentText = comment.getText();
        final PsiElement whitespace = comment.getNextSibling();
        assert whitespace != null;
        final String text = commentText.substring(2, commentText.length()-2);
        final String[] lines = text.split("\n");

        for(int i = lines.length-1; i>=1; i--){
            final PsiComment nextComment =
                    factory.createCommentFromText("//" + lines[i].trim() ,
                                                  parent);
            parent.addAfter(nextComment, comment);
            parent.addAfter(whitespace.copy(), comment);
        }
        final PsiComment newComment =
                factory.createCommentFromText("//" + lines[0], parent);
        comment.replace(newComment);
        codeStyleManager.reformat(parent);
    }

}
