package com.siyeh.ipp.base;

import com.intellij.psi.PsiElement;

public interface PsiElementPredicate{
    boolean satisfiedBy(PsiElement element);
}
