package com.siyeh.ipp.fqnames;

import com.intellij.psi.*;
import com.siyeh.ipp.PsiElementPredicate;

class FullyQualifiedNamePredicate implements PsiElementPredicate
{
    public boolean satisfiedBy(PsiElement element)
    {
        if(!(element instanceof PsiJavaCodeReferenceElement)
                || element instanceof PsiReferenceExpression)
        {
            return false;
        }

        PsiElement parent = element.getParent();
        while(parent instanceof PsiJavaCodeReferenceElement)
        {
            parent = parent.getParent();
        }
        if(parent instanceof PsiPackageStatement ||
                parent instanceof PsiImportStatement)
        {
            return false;
        }
        final String text = element.getText();
        return text.indexOf((int) '.') >= 0;
    }
}
