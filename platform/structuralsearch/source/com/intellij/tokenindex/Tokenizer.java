package com.intellij.tokenindex;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public interface Tokenizer {
  boolean visit(@NotNull PsiElement element, RecursiveTokenizingVisitor globalVisitor);

  void elementFinished(@NotNull PsiElement element, RecursiveTokenizingVisitor globalVisitor);
}
