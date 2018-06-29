// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

public class TypoAwareNameUtilMatchingTest extends NameUtilMatchingTestCase {
  public void testSimple() {
    assertMatches("doba", "fooBar");
    assertMatches("dobs", "fooBar");
  }

  public void testFragmentWithBrokenFirstLetter() {
    assertMatches("doba", "fooBar");
    assertMatches("doba", "fooBqBar");
    assertMatches("doba", "fooBqBar");
    assertMatches("awnutil", "SwiftNameUtil");
  }

  public void testNoErrorOnlyFragments() {
    assertDoesntMatch("foobar", "foovse");
  }

  public void testSwap() {
    assertMatches("braz", "barz");
    assertMatches("braz", "barvZoo");
  }
}
