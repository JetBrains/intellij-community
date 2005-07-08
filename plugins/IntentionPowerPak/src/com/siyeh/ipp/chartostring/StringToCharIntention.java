package com.siyeh.ipp.chartostring;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class StringToCharIntention extends Intention{
    @NotNull
    protected PsiElementPredicate getElementPredicate(){
        return new StringToCharPredicate();
    }

    public String getText(){
        return "Replace string literal with character";
    }

    public String getFamilyName(){
        return "Replace String With Char";
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{

        final PsiLiteralExpression stringLiteral =
                (PsiLiteralExpression) element;

        final String stringLiteralText = stringLiteral.getText();
        final String charLiteral = charForStringLiteral(stringLiteralText);
        replaceExpression(charLiteral, stringLiteral);
    }

    private static String charForStringLiteral(String stringLiteral){
        if("\"'\"".equals(stringLiteral)){
            return "'\\''";
        } else{
            return '\'' + stringLiteral
                    .substring(1, stringLiteral.length() - 1) +
                    '\'';
        }
    }
}
