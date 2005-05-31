package com.siyeh.ipp.integer;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.siyeh.ipp.base.PsiElementPredicate;

class ConvertIntegerToHexPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiLiteralExpression)){
            return false;
        }
        final PsiLiteralExpression expression = (PsiLiteralExpression) element;
        final PsiType type = expression.getType();
        if(type.equals(PsiType.INT) ||
                type.equals(PsiType.LONG)){
            final String text = expression.getText();

            return !(text.startsWith("0x") || text.startsWith("0X"));
        }
        if(type.equals(PsiType.DOUBLE) || type.equals(PsiType.FLOAT)){
            final PsiManager manager = expression.getManager();
            final LanguageLevel languageLevel =
                    manager.getEffectiveLanguageLevel();
            if(languageLevel.equals(LanguageLevel.JDK_1_3) ||
                    languageLevel.equals(LanguageLevel.JDK_1_4)){
                return false;
            }
            final String text = expression.getText();
            if(text == null){
                return false;
            }
            return !text.startsWith("0x") && !text.startsWith("0X");
        }
        return false;
    }
}
