package com.siyeh.ipp.chartostring;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;

public class StringToCharIntention extends Intention{
    protected PsiElementPredicate getElementPredicate(){
        return new StringToCharPredicate();
    }

    public String getText(){
        return "Replace string literal with character";
    }

    public String getFamilyName(){
        return "Replace String With Char";
    }

    public void invoke(Project project, Editor editor, PsiFile file)
            throws IncorrectOperationException{
        if(isFileReadOnly(project, file)){
            return;
        }

        final PsiLiteralExpression stringLiteral =
                (PsiLiteralExpression) findMatchingElement(file, editor);

        final String stringLiteralText = stringLiteral.getText();
        final String charLiteral = charForStringLiteral(stringLiteralText);
        replaceExpression(project, charLiteral, stringLiteral);
    }

    private static String charForStringLiteral(String stringLiteral){
        if("\"'\"".equals(stringLiteral))
        {
            return "'\\''";
        }
        else
        {
            return '\'' + stringLiteral.substring(1, stringLiteral.length()-1) +
                           '\'';
        }
    }

}
