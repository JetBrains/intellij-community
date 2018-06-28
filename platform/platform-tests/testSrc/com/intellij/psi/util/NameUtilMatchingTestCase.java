// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.ide.util.FileStructureDialog;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.text.Matcher;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;

public abstract class NameUtilMatchingTestCase extends TestCase {
  protected static MinusculeMatcher caseInsensitiveMatcher(String pattern) {
    return NameUtil.buildMatcher(pattern, NameUtil.MatchingCaseSensitivity.NONE);
  }

  protected static MinusculeMatcher fileStructureMatcher(String pattern) {
    return FileStructureDialog.createFileStructureMatcher(pattern);
  }

  protected static Matcher firstLetterMatcher(String pattern) {
    return NameUtil.buildMatcher(pattern, NameUtil.MatchingCaseSensitivity.FIRST_LETTER);
  }

  static void assertMatches(@NonNls String pattern, @NonNls String name) {
    assertTrue(pattern + " doesn't match " + name + "!!!", caseInsensitiveMatcher(pattern).matches(name));
  }

  static void assertDoesntMatch(@NonNls String pattern, @NonNls String name) {
    assertFalse(pattern + " matches " + name + "!!!", caseInsensitiveMatcher(pattern).matches(name));
  }

  protected static void assertPreference(@NonNls String pattern,
                                         @NonNls String less,
                                         @NonNls String more) {
    assertPreference(pattern, less, more, NameUtil.MatchingCaseSensitivity.FIRST_LETTER);
  }

  protected static void assertPreference(@NonNls String pattern,
                                         @NonNls String less,
                                         @NonNls String more,
                                         NameUtil.MatchingCaseSensitivity sensitivity) {
    assertPreference(NameUtil.buildMatcher(pattern, sensitivity), less, more);
  }

  protected static void assertPreference(MinusculeMatcher matcher, String less, String more) {
    int iLess = matcher.matchingDegree(less);
    int iMore = matcher.matchingDegree(more);
    assertTrue(iLess + ">=" + iMore + "; " + less + ">=" + more, iLess < iMore);
  }

  protected static void assertNoPreference(@NonNls String pattern,
                                           @NonNls String name1,
                                           @NonNls String name2,
                                           NameUtil.MatchingCaseSensitivity sensitivity) {
    MinusculeMatcher matcher = NameUtil.buildMatcher(pattern, sensitivity);
    assertEquals(matcher.matchingDegree(name1), matcher.matchingDegree(name2));
  }
}
