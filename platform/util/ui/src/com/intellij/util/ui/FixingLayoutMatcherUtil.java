// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.psi.codeStyle.AllOccurrencesMatcher;
import com.intellij.psi.codeStyle.FixingLayoutMatcher;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import org.jetbrains.annotations.NotNull;

public final class FixingLayoutMatcherUtil {
  private FixingLayoutMatcherUtil() {
  }

  public static FixingLayoutMatcher create(@NotNull String pattern,
                                           @NotNull NameUtil.MatchingCaseSensitivity options,
                                           String hardSeparators) {
    return new FixingLayoutMatcher(pattern, options, hardSeparators, KeyboardLayoutUtil::getAsciiForChar);
  }

  public static AllOccurrencesMatcher createAllOccurrencesMatcher(@NotNull String pattern,
                                                                  @NotNull NameUtil.MatchingCaseSensitivity options,
                                                                  String hardSeparators) {
    return new AllOccurrencesMatcher(pattern, options, hardSeparators, KeyboardLayoutUtil::getAsciiForChar);
  }

  public static NameUtil.MatcherBuilder buildLayoutFixingMatcher(@NotNull String pattern) {
    return NameUtil.buildMatcher(pattern).withAsciiToCharConverter(KeyboardLayoutUtil::getAsciiForChar);
  }

  @NotNull
  public static MinusculeMatcher buildLayoutFixingMatcher(@NotNull String pattern, @NotNull NameUtil.MatchingCaseSensitivity options) {
    return NameUtil.buildMatcher(pattern)
      .withCaseSensitivity(options)
      .withAsciiToCharConverter(KeyboardLayoutUtil::getAsciiForChar)
      .build();
  }
}
