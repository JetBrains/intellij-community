package com.siyeh.ipp.decls;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiVariable;
import com.siyeh.ipp.PsiElementPredicate;

class SimplifyVariablePredicate implements PsiElementPredicate
{
    public boolean satisfiedBy(PsiElement element)
    {
        if(!(element instanceof PsiVariable))
        {
            return false;
        }

        final PsiVariable var = (PsiVariable) element;
        final PsiTypeElement typeElement = var.getTypeElement();
        if (typeElement == null)
        {
            return false; // Could be true for enum constants.
        }

        final PsiType elementType = typeElement.getType();
        final PsiType type = var.getType();
        return elementType.getArrayDimensions() != type.getArrayDimensions();
    }
}
