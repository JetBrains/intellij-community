/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.TextRange;
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
  public FList<TextRange> matchingFragments(@NotNull String name) {
    FList<TextRange> ranges = super.matchingFragments(name);
    if (myFixedMatcher == null || ranges != null && !ranges.isEmpty()) return ranges;
    return myFixedMatcher.matchingFragments(name);
  }
}
