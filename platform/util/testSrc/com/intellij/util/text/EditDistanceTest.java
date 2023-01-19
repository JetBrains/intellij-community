// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EditDistanceTest {
  @Test
  public void levenshtein() {
    assertEquals(0, EditDistance.levenshtein("", "", true));
    assertEquals(0, EditDistance.levenshtein("AA", "AA", true));
    assertEquals(1, EditDistance.levenshtein("AA", "Aa", true));
    assertEquals(2, EditDistance.levenshtein("AA", "aa", true));
    assertEquals(2, EditDistance.levenshtein("ab", "ba", true));
  }

  @Test
  public void levenshteinCaseInsensitive() {
    assertEquals(0, EditDistance.levenshtein("", "", false));
    assertEquals(0, EditDistance.levenshtein("AA", "AA", false));
    assertEquals(0, EditDistance.levenshtein("AA", "Aa", false));
    assertEquals(0, EditDistance.levenshtein("AA", "aa", false));
    assertEquals(2, EditDistance.levenshtein("ab", "ba", false));
  }

  @Test
  public void optimalAlignment() {
    assertEquals(0, EditDistance.optimalAlignment("", "", true));
    assertEquals(2, EditDistance.optimalAlignment("", "ba", true));
    assertEquals(1, EditDistance.optimalAlignment("ab", "ba", true));
    assertEquals(2, EditDistance.optimalAlignment("AB", "ba", true));
    assertEquals(3, EditDistance.optimalAlignment("ca", "abc", true));
    assertEquals(4, EditDistance.optimalAlignment("abcd", "BADC", true));
    assertEquals(3, EditDistance.optimalAlignment("Ca", "abc", true));
  }

  @Test
  public void optimalAlignmentCaseInsensitive() {
    assertEquals(0, EditDistance.optimalAlignment("", "", false));
    assertEquals(2, EditDistance.optimalAlignment("", "ba", false));
    assertEquals(1, EditDistance.optimalAlignment("ab", "ba", false));
    assertEquals(1, EditDistance.optimalAlignment("AB", "ba", false));
    assertEquals(3, EditDistance.optimalAlignment("ca", "abc", false));
    assertEquals(2, EditDistance.optimalAlignment("abcd", "BADC", false));
    assertEquals(3, EditDistance.optimalAlignment("Ca", "abc", false));
  }
}