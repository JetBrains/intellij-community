package com.siyeh.ipp.integer;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.base.PsiElementPredicate;

class ConvertIntegerToOctalPredicate implements PsiElementPredicate
{
    public boolean satisfiedBy(PsiElement element)
    {
        if(!(element instanceof PsiLiteralExpression))
        {
            return false;
        }
        final PsiLiteralExpression expression = (PsiLiteralExpression) element;
        final PsiType type = expression.getType();
        if(!(type.equals(PsiType.INT) ||
                type.equals(PsiType.LONG)))
        {
            return false;
        }
        final String text = expression.getText();
        if(text == null || text.length() == 0)
        {
            return false;
        }
        if(text.startsWith("0x") || text.startsWith("0X"))
        {
            return true;
        }
        if("0".equals(text) || "0L".equals(text))
        {
            return false;
        }
        return text.charAt(0) != '0';
    }
}
