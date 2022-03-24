

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.util.text.matching.KeyboardLayoutUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 * @see NameUtil#buildMatcher(String)
 */
public class FixingLayoutMatcher extends MatcherWithFallback {
  public FixingLayoutMatcher(@NotNull String pattern,
                             @NotNull NameUtil.MatchingCaseSensitivity options,
                             String hardSeparators) {
    super(
      new MinusculeMatcherImpl(pattern, options, hardSeparators),
      withFixedLayout(pattern, options, hardSeparators)
    );
  }

  @Nullable
  public static String fixLayout(String pattern) {
    boolean hasLetters = false;
    boolean onlyWrongLetters = true;
    for (int i = 0; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      if (Character.isLetter(c)) {
        hasLetters = true;
        if (c <= '\u007f') {
          onlyWrongLetters = false;
          break;
        }
      }
    }

    if (hasLetters && onlyWrongLetters) {
      char[] alternatePattern = new char[pattern.length()];
      for (int i = 0; i < pattern.length(); i++) {
        char c = pattern.charAt(i);
        Character newC = KeyboardLayoutUtil.getAsciiForChar(c);
        alternatePattern[i] = newC == null ? c : newC;
      }

      return new String(alternatePattern);
    }
    return null;
  }

  @Nullable
  private static MinusculeMatcher withFixedLayout(@NotNull String pattern,
                                                  @NotNull NameUtil.MatchingCaseSensitivity options,
                                                  String hardSeparators) {
    String s = fixLayout(pattern);
    if (s != null && !s.equals(pattern)) {
      return new MinusculeMatcherImpl(s, options, hardSeparators);
    }

    return null;
  }
}