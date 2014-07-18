/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.patterns;

import junit.framework.TestCase;

import static com.intellij.patterns.StandardPatterns.string;

public class StringPatternTest extends TestCase {

  public void testString() {
    final ElementPattern pattern = string();
    assertFalse(string().accepts(new Object()));
    assertTrue(pattern.accepts(""));
  }

  public void testStartsWith() {
    final ElementPattern pattern = string().startsWith("abc");
    assertFalse(pattern.accepts(""));
    assertTrue(pattern.accepts("abcd"));
    assertTrue(pattern.accepts("abc"));

    assertFalse(string().startsWith("abc").accepts(new Object()));
  }

  public void testEndsWith() {
    final ElementPattern pattern = string().endsWith("abc");
    assertFalse(pattern.accepts(""));
    assertFalse(pattern.accepts("abcd"));
    assertTrue(pattern.accepts("abc"));
  }

  public void testLongerThan() {
    final ElementPattern pattern = string().longerThan(2);
    assertFalse(pattern.accepts(""));
    assertFalse(pattern.accepts("01"));
    assertTrue(pattern.accepts("012"));
  }

  public void testShorterThan() {
    final ElementPattern pattern = string().shorterThan(2);
    assertTrue(pattern.accepts(""));
    assertTrue(pattern.accepts("1"));
    assertFalse(pattern.accepts("12"));
  }

  public void testWithLength() {
    final ElementPattern pattern = string().withLength(2);
    assertFalse(pattern.accepts(""));
    assertFalse(pattern.accepts("1"));
    assertTrue(pattern.accepts("12"));
  }

  public void testContains() {
    final ElementPattern pattern = string().contains("abc");
    assertFalse(pattern.accepts(""));
    assertFalse(pattern.accepts("acb"));
    assertFalse(pattern.accepts("ABC"));
    assertTrue(pattern.accepts("01abcd"));
  }

  public void testContainsChars() {
    final ElementPattern pattern = string().containsChars("abc");
    assertFalse(pattern.accepts(""));
    assertFalse(pattern.accepts("ABC"));
    assertTrue(pattern.accepts("01a"));
    assertTrue(pattern.accepts("01c"));
    assertTrue(pattern.accepts("01b"));
  }

  public void testOneOfIgnoreCase() {
    final ElementPattern pattern = string().oneOfIgnoreCase("a", "B");
    assertFalse(pattern.accepts(""));
    assertFalse(pattern.accepts("ab"));
    assertFalse(pattern.accepts("d01x"));
    assertTrue(pattern.accepts("A"));
    assertTrue(pattern.accepts("b"));
  }
}
