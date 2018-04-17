// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.dupLocator.iterators.NodeIterator;
import org.jetbrains.annotations.NotNull;

public final class LightTopLevelMatchingHandler extends MatchingHandler implements DelegatingHandler {
  private final MatchingHandler myDelegate;

  public LightTopLevelMatchingHandler(@NotNull MatchingHandler delegate) {
    myDelegate = delegate;
  }

  @Override
  public boolean match(final PsiElement patternNode, final PsiElement matchedNode, final MatchContext matchContext) {
    return myDelegate.match(patternNode, matchedNode, matchContext);
  }

  @Override
  public boolean canMatch(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    return myDelegate.canMatch(patternNode, matchedNode, context);
  }

  @Override
  public boolean matchSequentially(final NodeIterator patternNodes, final NodeIterator matchNodes, final MatchContext context) {
    return myDelegate.matchSequentially(patternNodes, matchNodes, context);
  }

  @Override
  public boolean isMatchSequentiallySucceeded(final NodeIterator matchNodes) {
    return true;
  }

  @Override
  public boolean shouldAdvanceTheMatchFor(final PsiElement patternElement, final PsiElement matchedElement) {
    return myDelegate.shouldAdvanceTheMatchFor(patternElement, matchedElement);
  }

  @Override
  public MatchingHandler getDelegate() {
    return myDelegate;
  }
}