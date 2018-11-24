package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiElement;

public class CompositeFilter implements NodeFilter {
  private final NodeFilter first;
  private final NodeFilter second;

  public boolean accepts(PsiElement element) {
    return first.accepts(element) ||
           second.accepts(element);
  }

  public CompositeFilter(NodeFilter _first, NodeFilter _second) {
    first = _first;
    second = _second;
  }
}
