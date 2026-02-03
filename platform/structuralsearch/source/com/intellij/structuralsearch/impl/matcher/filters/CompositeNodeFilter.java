package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class CompositeNodeFilter implements NodeFilter {
  private final NodeFilter first;
  private final NodeFilter second;

  public CompositeNodeFilter(@NotNull NodeFilter _first, @NotNull NodeFilter _second) {
    first = _first;
    second = _second;
  }

  @Override
  public boolean accepts(PsiElement element) {
    return first.accepts(element) ||
           second.accepts(element);
  }
}
