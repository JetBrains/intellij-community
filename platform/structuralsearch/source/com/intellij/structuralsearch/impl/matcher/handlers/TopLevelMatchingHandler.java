/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.dupLocator.iterators.SiblingNodeIterator;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.iterators.SsrFilteringNodeIterator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class TopLevelMatchingHandler extends MatchingHandler implements DelegatingHandler {
  private final MatchingHandler delegate;

  public TopLevelMatchingHandler(@NotNull MatchingHandler _delegate) {
    delegate = _delegate;
    setFilter(_delegate.getFilter());
  }

  @Override
  public boolean match(final PsiElement patternNode, final PsiElement matchedNode, final MatchContext matchContext) {
    final boolean matched = delegate.match(patternNode, matchedNode, matchContext);

    if (matched) {
      List<PsiElement> matchedNodes = matchContext.getMatchedNodes();
      if (matchedNodes == null) {
        matchedNodes = new ArrayList<>();
        matchContext.setMatchedNodes(matchedNodes);
      }

      PsiElement elementToAdd = matchedNode;

      if (patternNode instanceof PsiComment && StructuralSearchUtil.isDocCommentOwner(matchedNode)) {
        // psicomment and psidoccomment are placed inside the psimember next to them so
        // simple topdown matching should do additional "dances" to cover this case.
        elementToAdd = matchedNode.getFirstChild();
        assert elementToAdd instanceof PsiComment;
      }

      matchedNodes.add(elementToAdd);
    }

    if ((!matched || matchContext.getOptions().isRecursiveSearch()) &&
        matchContext.getPattern().getStrategy().continueMatching(matchedNode) &&
        matchContext.shouldRecursivelyMatch()
       ) {
      matchContext.getMatcher().matchContext(
        new SsrFilteringNodeIterator(
          new SiblingNodeIterator(matchedNode.getFirstChild())
        )
      );
    }
    return matched;
  }

  @Override
  public boolean canMatch(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    return delegate.canMatch(patternNode, matchedNode, context);
  }

  @Override
  public boolean matchSequentially(final NodeIterator nodes, final NodeIterator nodes2, final MatchContext context) {
    return delegate.matchSequentially(nodes, nodes2, context);
  }

  @Override
  public boolean isMatchSequentiallySucceeded(final NodeIterator nodes2) {
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