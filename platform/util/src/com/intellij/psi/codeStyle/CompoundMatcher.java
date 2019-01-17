// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CompoundMatcher extends MinusculeMatcher {

  @NotNull
  private final MinusculeMatcher myFirstMatcher;

  @NotNull
  private final MinusculeMatcher mySecondMatcher;

  CompoundMatcher(@NotNull MinusculeMatcher firstMatcher,
                  @NotNull MinusculeMatcher nextMatcher) {
    super(firstMatcher.getPattern(), firstMatcher.getOptions(), firstMatcher.getHardSeparators());
    myFirstMatcher = firstMatcher;
    mySecondMatcher = nextMatcher;
  }

  @Override
  public boolean matches(@NotNull String name) {
    return myFirstMatcher.matches(name) || mySecondMatcher.matches(name);
  }

  @Nullable
  @Override
  public FList<TextRange> matchingFragments(@NotNull String name) {
    FList<TextRange> ranges = myFirstMatcher.matchingFragments(name);

    if (ranges != null && !ranges.isEmpty()) return ranges;
    return mySecondMatcher.matchingFragments(name);
  }
}
