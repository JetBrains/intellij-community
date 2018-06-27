// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.util.containers.FList;
import com.intellij.util.ui.KeyboardLayoutUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 *
 * @see NameUtil#buildMatcher(String)
 */
public class FixingLayoutMatcher extends MinusculeMatcher {

  @Nullable
  private final MinusculeMatcher myFixedMatcher;

  public FixingLayoutMatcher(@NotNull String pattern, @NotNull NameUtil.MatchingCaseSensitivity options, String hardSeparators) {
    super(pattern, options, hardSeparators);
    String s = fixLayout(pattern);
    myFixedMatcher = s == null ? null : new MinusculeMatcher(s, options, hardSeparators);
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

  @Override
  public boolean matches(@NotNull String name) {
    return super.matches(name) || myFixedMatcher != null && myFixedMatcher.matches(name);
  }

  @Nullable
  @Override
  public FList<Range> matchingFragments(@NotNull String name) {
    FList<Range> ranges = super.matchingFragments(name);
    if (myFixedMatcher == null || ranges != null && !ranges.isEmpty()) return ranges;
    return myFixedMatcher.matchingFragments(name);
  }
}
