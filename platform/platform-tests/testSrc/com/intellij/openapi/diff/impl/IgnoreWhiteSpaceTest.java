package com.intellij.openapi.diff.impl;

import junit.framework.TestCase;

public class IgnoreWhiteSpaceTest extends TestCase {
  private ComparisonPolicy myPolicy;

  public void testTrim() {
    myPolicy = ComparisonPolicy.TRIM_SPACE;
    Object[] keys = myPolicy.getLineWrappers(new String[]{"a b", " a b ", "\ta b", "a  b"});
    assertEquals(keys[0], keys[1]);
    assertEquals(keys[1], keys[2]);
    assertFalse(keys[2].equals(keys[3]));
    keys = myPolicy.getWrappers(new String[]{" a b", " a b ", " a b \n", "\ta b", "\n", "   "});
    assertEquals(keys[0], keys[3]);
    assertFalse(keys[0].equals(keys[1]));
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
    assertFalse(keys[1].equals(keys[4]));
    keys = myPolicy.getWrappers(new String[]{" ", "   ", "\t\n", "a"});
    assertEquals(keys[0], keys[1]);
    assertEquals(keys[1], keys[2]);
    assertFalse(keys[2].equals(keys[3]));
  }

  public static void assertEquals(Object obj1, Object obj2) {
    if (obj1 instanceof CharSequence && obj2 instanceof CharSequence) {
      assertEquals(obj1.toString(), obj2.toString());
      return;
    }

    assertEquals(obj1, obj2);
  }
}
