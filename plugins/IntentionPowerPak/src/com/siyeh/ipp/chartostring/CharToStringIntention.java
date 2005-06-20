package com.siyeh.ipp.chartostring;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class CharToStringIntention extends Intention{
    @NotNull
    protected PsiElementPredicate getElementPredicate(){
        return new CharToStringPredicate();
    }

    public String getText(){
        return "Replace character literal with string";
    }

    public String getFamilyName(){
        return "Replace Char With String";
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiLiteralExpression charLiteral =
                (PsiLiteralExpression) element;

        assert charLiteral != null;
        final String charLiteralText = charLiteral.getText();
        final String stringLiteral = stringForCharLiteral(charLiteralText);
        replaceExpression(stringLiteral, charLiteral);
    }

    private static String stringForCharLiteral(String charLiteral){
        if("'\"'".equals(charLiteral)){
            return "\"\\\"\"";
        } else{
            return '\"' + charLiteral.substring(1, charLiteral.length() - 1) +
                    '\"';
        }
    }
}
