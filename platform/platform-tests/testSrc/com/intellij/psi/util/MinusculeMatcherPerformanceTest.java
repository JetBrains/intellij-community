/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.psi.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.psi.util.NameUtilMatchingTest.assertDoesntMatch;
import static com.intellij.psi.util.NameUtilMatchingTest.assertMatches;

/**
 * @author peter
 */
public class MinusculeMatcherPerformanceTest extends TestCase {
  public void testPerformance() {
    @NonNls final String longName = "ThisIsAQuiteLongNameWithParentheses().Dots.-Minuses-_UNDERSCORES_digits239:colons:/slashes\\AndOfCourseManyLetters";
    final List<MinusculeMatcher> matching = new ArrayList<>();
    final List<MinusculeMatcher> nonMatching = new ArrayList<>();

    for (String s : ContainerUtil.ar("*", "*i", "*a", "*u", "T", "ti", longName, longName.substring(0, 20))) {
      matching.add(NameUtil.buildMatcher(s, NameUtil.MatchingCaseSensitivity.NONE));
    }
    for (String s : ContainerUtil.ar("A", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "tag")) {
      nonMatching.add(NameUtil.buildMatcher(s, NameUtil.MatchingCaseSensitivity.NONE));
    }

    PlatformTestUtil.startPerformanceTest("Matching", 5000, () -> {
      for (int i = 0; i < 100_000; i++) {
        for (MinusculeMatcher matcher : matching) {
          Assert.assertTrue(matcher.toString(), matcher.matches(longName));
          matcher.matchingDegree(longName);
        }
        for (MinusculeMatcher matcher : nonMatching) {
          Assert.assertFalse(matcher.toString(), matcher.matches(longName));
        }
      }
    }).assertTiming();
  }

  public void testOnlyUnderscoresPerformance() {
    PlatformTestUtil.startPerformanceTest(getName(), 120, () -> {
      String small = StringUtil.repeat("_", 50000);
      String big = StringUtil.repeat("_", small.length() + 1);
      assertMatches("*" + small, big);
      assertDoesntMatch("*" + big, small);
    }).assertTiming();
  }

  public void testRepeatedLetterPerformance() {
    PlatformTestUtil.startPerformanceTest(getName(), 30, () -> {
      String big = StringUtil.repeat("Aaaaaa", 50000);
      assertMatches("aaaaaaaaaaaaaaaaaaaaaaaa", big);
      assertDoesntMatch("aaaaaaaaaaaaaaaaaaaaaaaab", big);
    }).assertTiming();
  }

}
