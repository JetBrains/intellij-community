package com.siyeh.ipp.chartostring;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;

public class CharToStringIntention extends Intention{
    protected PsiElementPredicate getElementPredicate(){
        return new CharToStringPredicate();
    }

    public String getText(){
        return "Replace character literal with string";
    }

    public String getFamilyName(){
        return "Replace Char With String";
    }

    public void invoke(Project project, Editor editor, PsiFile file)
            throws IncorrectOperationException{
        if(isFileReadOnly(project, file)){
            return;
        }

        final PsiLiteralExpression charLiteral =
                (PsiLiteralExpression) findMatchingElement(file, editor);

        final String charLiteralText = charLiteral.getText();
        final String stringLiteral = stringForCharLiteral(charLiteralText);
        replaceExpression(project, stringLiteral, charLiteral);
    }

    private static String stringForCharLiteral(String charLiteral){
        if("'\"'".equals(charLiteral))
        {
            return "\"\\\"\"";
        }
        else
        {
            return '\"' + charLiteral.substring(1, charLiteral.length()-1) +
                           '\"';
        }
    }

}
