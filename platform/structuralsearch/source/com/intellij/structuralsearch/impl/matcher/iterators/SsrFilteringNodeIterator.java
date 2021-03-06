// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.iterators;

import com.intellij.dupLocator.iterators.*;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class SsrFilteringNodeIterator extends FilteringNodeIterator {
  private SsrFilteringNodeIterator(@NotNull NodeIterator iterator) {
    super(iterator, LexicalNodesFilter.getInstance());
  }

  private SsrFilteringNodeIterator(@NotNull PsiElement element) {
    this(SiblingNodeIterator.create(element));
  }

  public static NodeIterator create(@Nullable PsiElement element) {
    return (element == null) ? SingleNodeIterator.EMPTY : new SsrFilteringNodeIterator(element);
  }

  public static NodeIterator create(PsiElement @NotNull [] elements) {
    return new SsrFilteringNodeIterator(new ArrayBackedNodeIterator(elements));
  }
}
