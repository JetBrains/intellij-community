package com.siyeh.ipp.exceptions;

import com.intellij.psi.PsiType;

import java.util.Comparator;

class HeirarchicalTypeComparator implements Comparator
{
    public int compare(Object o1, Object o2)
    {
        final PsiType type1 = (PsiType) o1;
        final PsiType type2 = (PsiType) o2;
        if(type1.isAssignableFrom(type2))
        {
            return 1;
        }
        if(type2.isAssignableFrom(type1))
        {
            return -1;
        }
        return 0;
    }
}
