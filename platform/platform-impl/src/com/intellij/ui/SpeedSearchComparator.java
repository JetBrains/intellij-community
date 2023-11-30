// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.text.NameUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author Konstantin Bulenkov
*/
public class SpeedSearchComparator {
  protected String myRecentSearchText;
  private MinusculeMatcher myMinusculeMatcher;
  private final boolean myShouldMatchFromTheBeginning;
  private final boolean myShouldMatchCamelCase;
  private final String myHardSeparators;

  public SpeedSearchComparator() {
    this(true);
  }

  public SpeedSearchComparator(boolean shouldMatchFromTheBeginning) {
    this(shouldMatchFromTheBeginning, false);
  }

  public SpeedSearchComparator(boolean shouldMatchFromTheBeginning, boolean shouldMatchCamelCase) {
    this(shouldMatchFromTheBeginning, shouldMatchCamelCase, "");
  }

  public SpeedSearchComparator(boolean shouldMatchFromTheBeginning, boolean shouldMatchCamelCase, @NotNull String hardSeparators) {
    myShouldMatchFromTheBeginning = shouldMatchFromTheBeginning;
    myShouldMatchCamelCase = shouldMatchCamelCase;
    myHardSeparators = hardSeparators;
  }

  public int matchingDegree(String pattern, String text) {
    return obtainMatcher(pattern).matchingDegree(text);
  }

  public @Nullable Iterable<TextRange> matchingFragments(@NotNull String pattern, @NotNull String text) {
    return obtainMatcher(pattern).matchingFragments(text);
  }

  private MinusculeMatcher obtainMatcher(@NotNull String pattern) {
    if (myRecentSearchText == null || !myRecentSearchText.equals(pattern)) {
      myRecentSearchText = pattern;
      if (myShouldMatchCamelCase) {
        pattern = StringUtil.join(NameUtilCore.nameToWords(pattern), "*");
      }
      if (!myShouldMatchFromTheBeginning && !pattern.startsWith("*")) {
        pattern = "*" + pattern;
      }
      myMinusculeMatcher = createMatcher(pattern);
    }
    return myMinusculeMatcher;
  }

  private @NotNull MinusculeMatcher createMatcher(@NotNull String pattern) {
    return NameUtil.buildMatcher(pattern).withSeparators(myHardSeparators).build();
  }

  public String getRecentSearchText() {
    return myRecentSearchText;
  }
}
