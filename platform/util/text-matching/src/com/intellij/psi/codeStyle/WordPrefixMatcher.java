// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.text.Matcher;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class WordPrefixMatcher implements Matcher {
  private final String[] myPatternWords;
  private final String[] myFallbackPatternWords;

  public WordPrefixMatcher(String pattern) {
    myPatternWords = splitToWords(pattern);
    String fixedLayout = FixingLayoutMatcher.fixLayout(pattern);
    myFallbackPatternWords = fixedLayout != null && !fixedLayout.equals(pattern) ? NameUtil.nameToWords(fixedLayout) : null;
  }

  @Override
  public boolean matches(@NotNull String name) {
    String[] nameWords = splitToWords(name);
    return matches(myPatternWords, nameWords) || myFallbackPatternWords != null && matches(myFallbackPatternWords, nameWords);
  }

  private static @NotNull String[] splitToWords(@NotNull String string) {
    return string.split("[^a-zA-Z]+");
  }

  private static boolean matches(String[] patternWords, String[] nameWords) {
    return Arrays.stream(patternWords).allMatch(
      pw -> Arrays.stream(nameWords).anyMatch(nw -> StringUtilRt.startsWithIgnoreCase(nw, pw))
    );
  }
}
