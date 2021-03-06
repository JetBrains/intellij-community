// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.iterators.SsrFilteringNodeIterator;
import org.jetbrains.annotations.NotNull;

public final class TopLevelMatchingHandler extends MatchingHandler implements DelegatingHandler {
  private final MatchingHandler delegate;

  public TopLevelMatchingHandler(@NotNull MatchingHandler _delegate) {
    delegate = _delegate;
    setFilter(_delegate.getFilter());
  }

  @Override
  public boolean match(final PsiElement patternNode, final PsiElement matchedNode, final @NotNull MatchContext matchContext) {
    final boolean matched = delegate.match(patternNode, matchedNode, matchContext);

    if (matched) {
      PsiElement elementToAdd = matchedNode;

      if (patternNode instanceof PsiComment && StructuralSearchUtil.isDocCommentOwner(matchedNode)) {
        // psicomment and psidoccomment are placed inside the psimember next to them so
        // simple topdown matching should do additional "dances" to cover this case.
        elementToAdd = matchedNode.getFirstChild();
        assert elementToAdd instanceof PsiComment;
      }

      matchContext.addMatchedNode(elementToAdd);
    }

    if ((!matched || matchContext.getOptions().isRecursiveSearch()) &&
        matchContext.getPattern().getStrategy().continueMatching(matchedNode) &&
        matchContext.shouldRecursivelyMatch()
       ) {
      final PsiElement child = matchedNode.getFirstChild();
      if (child != null) {
        matchContext.getMatcher().matchContext(SsrFilteringNodeIterator.create(child));
      }
    }
    return matched;
  }

  @Override
  public boolean canMatch(@NotNull PsiElement patternNode, PsiElement matchedNode, @NotNull MatchContext context) {
    return delegate.canMatch(patternNode, matchedNode, context);
  }

  @Override
  public boolean matchSequentially(final @NotNull NodeIterator patternNodes, final @NotNull NodeIterator matchNodes, final @NotNull MatchContext context) {
    return delegate.matchSequentially(patternNodes, matchNodes, context);
  }

  @Override
  public boolean isMatchSequentiallySucceeded(final @NotNull NodeIterator matchNodes) {
    return true;
  }

  @Override
  public boolean shouldAdvanceTheMatchFor(final PsiElement patternElement, final PsiElement matchedElement) {
    return delegate.shouldAdvanceTheMatchFor(patternElement, matchedElement);
  }

  @Override
  public MatchingHandler getDelegate() {
    return delegate;
  }
}