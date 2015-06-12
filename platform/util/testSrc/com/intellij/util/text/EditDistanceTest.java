/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
    assertEquals(1, EditDistance.optimalAlignment("ab", "ba", true));
    assertEquals(2, EditDistance.optimalAlignment("AB", "ba", true));
    assertEquals(3, EditDistance.optimalAlignment("ca", "abc", true));
    assertEquals(3, EditDistance.optimalAlignment("Ca", "abc", true));
  }

  @Test
  public void optimalAlignmentCaseInsensitive() {
    assertEquals(1, EditDistance.optimalAlignment("ab", "ba", false));
    assertEquals(1, EditDistance.optimalAlignment("AB", "ba", false));
    assertEquals(3, EditDistance.optimalAlignment("ca", "abc", false));
    assertEquals(3, EditDistance.optimalAlignment("Ca", "abc", false));
  }
}