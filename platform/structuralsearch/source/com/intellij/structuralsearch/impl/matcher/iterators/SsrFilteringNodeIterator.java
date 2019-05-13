package com.intellij.structuralsearch.impl.matcher.iterators;

import com.intellij.dupLocator.iterators.FilteringNodeIterator;
import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.dupLocator.iterators.SiblingNodeIterator;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;

/**
 * @author Eugene.Kudelevsky
 */
public class SsrFilteringNodeIterator extends FilteringNodeIterator {
  public SsrFilteringNodeIterator(final NodeIterator iterator) {
    super(iterator, LexicalNodesFilter.getInstance());
  }

  public SsrFilteringNodeIterator(final PsiElement element) {
    this(new SiblingNodeIterator(element));
  }
}
