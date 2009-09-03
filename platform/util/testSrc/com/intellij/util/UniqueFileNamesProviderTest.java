package com.intellij.util;

import junit.framework.TestCase;

public class UniqueFileNamesProviderTest extends TestCase{
  public void test1() throws Exception {
    UniqueFileNamesProvider p = new UniqueFileNamesProvider();
    assertEquals("aaa", p.suggestName("aaa"));
    assertEquals("bbb", p.suggestName("bbb"));
    assertEquals("Bbb2", p.suggestName("Bbb"));
    assertEquals("aaa3", p.suggestName("aaa"));
    assertEquals("aaa4", p.suggestName("aaa"));
    assertEquals("a_b_c", p.suggestName("a+b+c"));
    assertEquals("_", p.suggestName(null));
    assertEquals("_7", p.suggestName(""));
  }
}
