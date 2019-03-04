/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl;

import junit.framework.TestCase;

import static org.junit.Assert.assertNotEquals;

public class IgnoreWhiteSpaceTest extends TestCase {
  private ComparisonPolicy myPolicy;

  public void testTrim() {
    myPolicy = ComparisonPolicy.TRIM_SPACE;
    Object[] keys = myPolicy.getLineWrappers(new String[]{"a b", " a b ", "\ta b", "a  b"});
    assertEquals(keys[0], keys[1]);
    assertEquals(keys[1], keys[2]);
    assertNotEquals(keys[2], keys[3]);
    keys = myPolicy.getWrappers(new String[]{" a b", " a b ", " a b \n", "\ta b", "\n", "   "});
    assertEquals(keys[0], keys[3]);
    assertNotEquals(keys[0], keys[1]);
    assertEquals(" a b", keys[2]);
    assertEquals("", keys[4]);
    assertEquals("", keys[5]);
  }

  public void testIgnore() {
    myPolicy = ComparisonPolicy.IGNORE_SPACE;
    Object[] keys = myPolicy.getLineWrappers(new String[]{"a b", " a b", " a  b ", "ab", " b a"});
    assertEquals(keys[0], keys[1]);
    assertEquals(keys[1], keys[2]);
    assertEquals(keys[2], keys[3]);
    assertNotEquals(keys[1], keys[4]);
    keys = myPolicy.getWrappers(new String[]{" ", "   ", "\t\n", "a"});
    assertEquals(keys[0], keys[1]);
    assertEquals(keys[1], keys[2]);
    assertNotEquals(keys[2], keys[3]);
  }

  public static void assertEquals(Object obj1, Object obj2) {
    if (obj1 instanceof CharSequence && obj2 instanceof CharSequence) {
      assertEquals(obj1.toString(), obj2.toString());
      return;
    }

    assertEquals(obj1, obj2);
  }
}
