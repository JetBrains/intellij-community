package com.siyeh.ipp.shift;

import com.intellij.psi.*;

class ShiftUtils
{
    private ShiftUtils()
    {
        super();
    }

    public static boolean isPowerOfTwo(PsiExpression rhs)
    {
        if(!(rhs instanceof PsiLiteralExpression))
        {
            return false;
        }
        final PsiLiteralExpression literal = (PsiLiteralExpression) rhs;
        final Object value = literal.getValue();
        if(!(value instanceof Number))
        {
            return false;
        }
        if(value instanceof Double || value instanceof Float)
        {
            return false;
        }
        int intValue = ((Number) value).intValue();
        if(intValue <= 0)
        {
            return false;
        }
        while(intValue % 2 == 0)
        {
            intValue >>= 1;
        }
        return intValue == 1;
    }

    public static int getLogBase2(PsiExpression rhs)
    {
        final PsiLiteralExpression literal = (PsiLiteralExpression) rhs;
        final Object value = literal.getValue();
        int intValue = ((Number) value).intValue();
        int log = 0;
        while(intValue % 2 == 0)
        {
            intValue >>= 1;
            log++;
        }
        return log;
    }

    public static int getExpBase2(PsiExpression rhs)
    {
        final PsiLiteralExpression literal = (PsiLiteralExpression) rhs;
        final Object value = literal.getValue();
        final int intValue = ((Number) value).intValue();
        int exp = 1;
        for(int i = 0; i < intValue; i++)
        {
            exp <<= 1;
        }
        return exp;
    }

    public static boolean isIntegral(final PsiType lhsType)
    {
        return lhsType!=null &&
                (lhsType.equals(PsiType.INT)
                || lhsType.equals(PsiType.SHORT)
                || lhsType.equals(PsiType.LONG)
                || lhsType.equals(PsiType.BYTE));
    }

    public static boolean isIntLiteral(PsiExpression rhs)
    {
        if(!(rhs instanceof PsiLiteralExpression))
        {
            return false;
        }
        final PsiLiteralExpression literal = (PsiLiteralExpression) rhs;
        final Object value = literal.getValue();
        if(!(value instanceof Number))
        {
            return false;
        }
        if(value instanceof Double || value instanceof Float)
        {
            return false;
        }
        return true;
    }
}
