package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiElement;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 28.12.2003
 * Time: 0:13:19
 * To change this template use Options | File Templates.
 */
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
