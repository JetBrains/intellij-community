/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author Konstantin Bulenkov
*/
public class SpeedSearchComparator {
  private MinusculeMatcher myMinusculeMatcher;
  private String myRecentSearchText;
  private final boolean myShouldMatchFromTheBeginning;
  private final boolean myShouldMatchCamelCase;

  public SpeedSearchComparator() {
    this(true);
  }

  public SpeedSearchComparator(boolean shouldMatchFromTheBeginning) {
    this(shouldMatchFromTheBeginning, false);
  }
  
  public SpeedSearchComparator(boolean shouldMatchFromTheBeginning, boolean shouldMatchCamelCase) {
    myShouldMatchFromTheBeginning = shouldMatchFromTheBeginning;
    myShouldMatchCamelCase = shouldMatchCamelCase;
  }

  public int matchingDegree(String pattern, String text) {
    return obtainMatcher(pattern).matchingDegree(text);
  }

  @Nullable
  public Iterable<TextRange> matchingFragments(@NotNull String pattern, @NotNull String text) {
    return obtainMatcher(pattern).matchingFragments(text);
  }

  private MinusculeMatcher obtainMatcher(@NotNull String pattern) {
    if (myRecentSearchText == null || !myRecentSearchText.equals(pattern)) {
      myRecentSearchText = pattern;
      if (myShouldMatchCamelCase) {
        pattern = StringUtil.join(NameUtil.nameToWords(pattern), "*");
      }
      if (!myShouldMatchFromTheBeginning && !pattern.startsWith("*")) {
        pattern = "*" + pattern;
      }
      myMinusculeMatcher = createMatcher(pattern);
    }
    return myMinusculeMatcher;
  }

  @NotNull
  protected MinusculeMatcher createMatcher(@NotNull String pattern) {
    return new MinusculeMatcher(pattern, NameUtil.MatchingCaseSensitivity.NONE);
  }

  public String getRecentSearchText() {
    return myRecentSearchText;
  }
}
