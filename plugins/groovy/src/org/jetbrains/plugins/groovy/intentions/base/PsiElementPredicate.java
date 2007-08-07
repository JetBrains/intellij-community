package org.jetbrains.plugins.groovy.intentions.base;

import com.intellij.psi.PsiElement;

public interface PsiElementPredicate {
  boolean satisfiedBy(PsiElement element);
}
