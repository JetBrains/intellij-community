// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class MatcherWithFallback extends MinusculeMatcher {
  @NotNull
  private final MinusculeMatcher myMainMatcher;

  @Nullable
  private final MinusculeMatcher myFallbackMatcher;

  MatcherWithFallback(@NotNull MinusculeMatcher mainMatcher,
                      @Nullable MinusculeMatcher fallbackMatcher) {
    myMainMatcher = mainMatcher;
    myFallbackMatcher = fallbackMatcher;
  }

  @NotNull
  @Override
  public String getPattern() {
    return myMainMatcher.getPattern();
  }

  @Override
  public boolean matches(@NotNull String name) {
    return myMainMatcher.matches(name) ||
           myFallbackMatcher != null && myFallbackMatcher.matches(name);
  }

  @Nullable
  @Override
  public FList<TextRange> matchingFragments(@NotNull String name) {
    FList<TextRange> mainRanges = myMainMatcher.matchingFragments(name);
    boolean useMainRanges = mainRanges != null && !mainRanges.isEmpty() || myFallbackMatcher == null;
    return useMainRanges ? mainRanges : myFallbackMatcher.matchingFragments(name);
  }

  @Override
  public int matchingDegree(@NotNull String name, boolean valueStartCaseMatch) {
    FList<TextRange> mainRanges = myMainMatcher.matchingFragments(name);
    boolean useMainRanges = mainRanges != null && !mainRanges.isEmpty() || myFallbackMatcher == null;

    return useMainRanges ? myMainMatcher.matchingDegree(name, valueStartCaseMatch, mainRanges)
                         : myFallbackMatcher.matchingDegree(name, valueStartCaseMatch);
  }

  @Override
  public int matchingDegree(@NotNull String name, boolean valueStartCaseMatch, @Nullable FList<? extends TextRange> fragments) {
    FList<TextRange> mainRanges = myMainMatcher.matchingFragments(name);
    boolean useMainRanges = mainRanges != null && !mainRanges.isEmpty() || myFallbackMatcher == null;

    return useMainRanges ? myMainMatcher.matchingDegree(name, valueStartCaseMatch, fragments)
                         : myFallbackMatcher.matchingDegree(name, valueStartCaseMatch, fragments);
  }

  @Override
  public String toString() {
    return "MatcherWithFallback{" +
           "myMainMatcher=" + myMainMatcher +
           ", myFallbackMatcher=" + myFallbackMatcher +
           '}';
  }
}
