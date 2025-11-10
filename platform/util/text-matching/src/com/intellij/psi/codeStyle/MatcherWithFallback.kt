// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class MatcherWithFallback extends MinusculeMatcher {
  private final @NotNull MinusculeMatcher myMainMatcher;

  private final @Nullable MinusculeMatcher myFallbackMatcher;

  MatcherWithFallback(@NotNull MinusculeMatcher mainMatcher,
                      @Nullable MinusculeMatcher fallbackMatcher) {
    myMainMatcher = mainMatcher;
    myFallbackMatcher = fallbackMatcher;
  }

  @Override
  public @NotNull String getPattern() {
    return myMainMatcher.getPattern();
  }

  @Override
  public boolean matches(@NotNull String name) {
    return myMainMatcher.matches(name) ||
           myFallbackMatcher != null && myFallbackMatcher.matches(name);
  }

  @Override
  public @Nullable FList<TextRange> matchingFragments(@NotNull String name) {
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
