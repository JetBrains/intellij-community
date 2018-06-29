// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class FixingLayoutTypoTolerantMatcher extends TypoTolerantMatcher {

  @Nullable
  private final MinusculeMatcher myFixedMatcher;

  public FixingLayoutTypoTolerantMatcher(@NotNull String pattern, @NotNull NameUtil.MatchingCaseSensitivity options, String hardSeparators) {
    super(pattern, options, hardSeparators);
    String s = FixingLayoutMatcher.fixLayout(pattern);
    myFixedMatcher = s == null ? null : new TypoTolerantMatcher(s, options, hardSeparators);
  }

  @Override
  public boolean matches(@NotNull String name) {
    return super.matches(name) || myFixedMatcher != null && myFixedMatcher.matches(name);
  }

  @Nullable
  @Override
  public FList<TextRange> matchingFragments(@NotNull String name) {
    FList<TextRange> ranges = super.matchingFragments(name);
    if (myFixedMatcher == null || ranges != null && !ranges.isEmpty()) return ranges;
    return myFixedMatcher.matchingFragments(name);
  }
}