// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    return (Iterable)obtainMatcher(pattern).matchingFragments(text); //todo better generics???
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
    return NameUtil.buildMatcher(pattern).build();
  }

  public String getRecentSearchText() {
    return myRecentSearchText;
  }
}
