package com.siyeh.ipp.integer;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.base.PsiElementPredicate;

class ConvertIntegerToHexPredicate implements PsiElementPredicate
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

        return !(text.startsWith("0x") || text.startsWith("0X"));
    }
}
