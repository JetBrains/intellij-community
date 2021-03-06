// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.handlers;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import org.jetbrains.annotations.NotNull;

/**
 * Handles simplest type of the match: one node from the pattern to one node of the code.
 */
public final class SimpleHandler extends MatchingHandler {
  /**
   * Matches given handler node against given value.
   * @param matchedNode for matching
   * @param context of the matching
   * @return true if matching was successful and false otherwise
   */
  @Override
  public boolean match(PsiElement patternNode, PsiElement matchedNode, @NotNull MatchContext context) {
    if (!super.match(patternNode, matchedNode, context)) return false;
    return context.getMatcher().match(patternNode, matchedNode);
  }
}
