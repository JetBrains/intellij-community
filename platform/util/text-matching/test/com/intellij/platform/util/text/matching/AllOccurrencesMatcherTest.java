// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.text.matching;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.AllOccurrencesMatcher;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class AllOccurrencesMatcherTest {
  @Test
  void simpleCase() {
    MinusculeMatcher matcher = AllOccurrencesMatcher.create("*fooBar", NameUtil.MatchingCaseSensitivity.NONE, "");
    assertIterableEquals(matcher.matchingFragments("fooBarFooBar"), List.of(new TextRange(0, 6), new TextRange(6, 12)));
    assertIterableEquals(matcher.matchingFragments("fooBarFooBuzzBar"), List.of(new TextRange(0, 6), new TextRange(6, 9), new TextRange(13, 16)));
    assertIterableEquals(matcher.matchingFragments("fooBarBuzzFoo"), List.of(new TextRange(0, 6)));
    assertIterableEquals(matcher.matchingFragments("fooBarBuzzFooBuzzBar"), List.of(new TextRange(0, 6), new TextRange(10, 13), new TextRange(17, 20)));
    assertIterableEquals(matcher.matchingFragments("fooBuzzFooBuzzBar"), List.of(new TextRange(0, 3), new TextRange(14, 17)));
  }
}