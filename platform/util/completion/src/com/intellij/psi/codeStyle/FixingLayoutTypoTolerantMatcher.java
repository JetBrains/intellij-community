// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

class FixingLayoutTypoTolerantMatcher {

  static MinusculeMatcher create(@NotNull String pattern,
                                 @NotNull NameUtil.MatchingCaseSensitivity options,
                                 String hardSeparators,
                                 @Nullable Function<Character, Character> asciiToCharConverter) {
    TypoTolerantMatcher mainMatcher = new TypoTolerantMatcher(pattern, options, hardSeparators);
    String s = FixingLayoutMatcher.fixLayout(pattern, asciiToCharConverter);

    if (s != null && !s.equals(pattern)) {
      TypoTolerantMatcher fallbackMatcher = new TypoTolerantMatcher(s, options, hardSeparators);
      return new MatcherWithFallback(mainMatcher, fallbackMatcher);
    } else {
      return mainMatcher;
    }
  }
}