// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author gregsh
 */
public class UniqueNameGeneratorTest {
  @Test
  public void testEmpty() {
    UniqueNameGenerator generator = new UniqueNameGenerator();
    assertEquals("", generator.generateUniqueName(""));
    assertEquals("2", generator.generateUniqueName(""));
    assertEquals("3", generator.generateUniqueName(""));
  }

  @Test
  public void testSpaces() {
    UniqueNameGenerator generator = new UniqueNameGenerator();
    assertEquals("", generator.generateUniqueName(" "));
    assertEquals("2", generator.generateUniqueName(" "));
    assertEquals("3", generator.generateUniqueName(" "));
  }

  @Test
  public void testQwerty() {
    UniqueNameGenerator generator = new UniqueNameGenerator();
    assertEquals("qwerty", generator.generateUniqueName("qwerty"));
    assertEquals("qwerty2", generator.generateUniqueName("qwerty"));
    assertEquals("qwerty3", generator.generateUniqueName("qwerty"));
  }

  @Test
  public void testPrefixSuffix() {
    UniqueNameGenerator generator = new UniqueNameGenerator();
    assertEquals("qwerty", generator.generateUniqueName("qwerty", "", "", " [", "]"));
    assertEquals("qwerty [2]", generator.generateUniqueName("qwerty", "", "", " [", "]"));
    assertEquals("qwerty [3]", generator.generateUniqueName("qwerty", "", "", " [", "]"));
  }
}
