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

import com.intellij.dupLocator.equivalence.EquivalenceDescriptor;
import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.dupLocator.util.DuplocatorUtil;
import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class SkippingHandler extends MatchingHandler implements DelegatingHandler {

  private final MatchingHandler myDelegate;

  public SkippingHandler(@NotNull MatchingHandler delegate) {
    myDelegate = delegate;
  }

  @Override
  public boolean match(PsiElement patternNode, PsiElement matchedNode, final MatchContext matchContext) {
    if (patternNode == null || matchedNode == null || matchedNode.getClass() == patternNode.getClass()) {
      return myDelegate.match(patternNode, matchedNode, matchContext);
    }

    /*if (patternNode != null && matchedNode != null && patternNode.getClass() == matchedNode.getClass()) {
      //return myDelegate.match(patternNode, matchedNode, matchContext);
    }*/
    final PsiElement newPatternNode = skipNodeIfNeccessary(patternNode);
    matchedNode = skipNodeIfNeccessary(matchedNode);

    if (newPatternNode != patternNode) {
      return matchContext.getPattern().getHandler(newPatternNode).match(newPatternNode, matchedNode, matchContext);
    }

    return myDelegate.match(patternNode, matchedNode, matchContext);
  }

  @Override
  public boolean canMatch(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    final PsiElement newPatternNode = skipNodeIfNeccessary(patternNode);
    if (newPatternNode != patternNode) {
      return context.getPattern().getHandler(newPatternNode).canMatch(newPatternNode, matchedNode, context);
    }
    return myDelegate.canMatch(patternNode, matchedNode, context);
  }

  @Override
  public boolean matchSequentially(final NodeIterator nodes, final NodeIterator nodes2, final MatchContext context) {
    return myDelegate.matchSequentially(nodes, nodes2, context);
  }

  @Override
  protected boolean isMatchSequentiallySucceeded(final NodeIterator nodes2) {
    return myDelegate.isMatchSequentiallySucceeded(nodes2);
  }

  @Override
  public MatchingHandler getDelegate() {
    return myDelegate;
  }

  @Nullable
  public static PsiElement getOnlyNonWhitespaceChild(PsiElement element) {
    PsiElement onlyChild = null;
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (DuplocatorUtil.isIgnoredNode(element) || child.getTextLength() == 0) {
        continue;
      }
      if (onlyChild != null) {
        return null;
      }
      onlyChild = child;
    }
    return onlyChild;
  }

  @Nullable
  public static PsiElement skipNodeIfNeccessary(PsiElement element) {
    return skipNodeIfNeccessary(element, null, null);
  }

  @Nullable
  public static PsiElement skipNodeIfNeccessary(PsiElement element, EquivalenceDescriptor descriptor, NodeFilter filter) {
    return DuplocatorUtil.skipNodeIfNeccessary(element, descriptor, filter != null ? filter : LexicalNodesFilter.getInstance());
  }
}
