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
  public boolean matchSequentially(final NodeIterator nodes, final NodeIterator nodes2, final MatchContext context) {
    return myDelegate.matchSequentially(nodes, nodes2, context);
  }

  @Override
  public boolean isMatchSequentiallySucceeded(final NodeIterator nodes2) {
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