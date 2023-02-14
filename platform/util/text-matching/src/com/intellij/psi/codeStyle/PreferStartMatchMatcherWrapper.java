// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PreferStartMatchMatcherWrapper extends MinusculeMatcher {
  public static final int START_MATCH_WEIGHT = 10000;
  private final @NotNull MinusculeMatcher myDelegateMatcher;

  public PreferStartMatchMatcherWrapper(@NotNull MinusculeMatcher matcher) {
    myDelegateMatcher = matcher;
  }

  @Override
  public @NotNull String getPattern() {
    return myDelegateMatcher.getPattern();
  }

  @Override
  public FList<TextRange> matchingFragments(@NotNull String name) {
    return myDelegateMatcher.matchingFragments(name);
  }

  @Override
  public int matchingDegree(@NotNull String name,
                            boolean valueStartCaseMatch,
                            @Nullable FList<? extends TextRange> fragments) {
    int degree = myDelegateMatcher.matchingDegree(name, valueStartCaseMatch, fragments);
    if (fragments == null || fragments.isEmpty()) return degree;

    if (fragments.getHead().getStartOffset() == 0) degree += START_MATCH_WEIGHT;
    return degree;
  }
}
