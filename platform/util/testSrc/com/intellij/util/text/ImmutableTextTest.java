// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;

public class ImmutableTextTest extends UsefulTestCase {

  public void testTextRemainsBalancedAfterAppends() {
    ImmutableText a = ImmutableText.valueOf(StringUtil.repeat("a", 10 * 64));
    ImmutableText b = ImmutableText.valueOf(StringUtil.repeat("b", 20 * 64));
    ImmutableText c = ImmutableText.valueOf(StringUtil.repeat("c", 20 * 64));
    ImmutableText ab = a.insert(a.length(), b);
    ImmutableText abc = ab.insert(ab.length(), c);

    ImmutableText x = ImmutableText.valueOf(StringUtil.repeat("x", 64));
    ImmutableText xabc = abc.insert(0, x);

    assertBalanced(xabc.myNode);
  }

  public void testDeleteAllPerformance() {
    ImmutableText original = ImmutableText.valueOf(StringUtil.repeat("abcdefghij", 1_900_000));

    PlatformTestUtil.startPerformanceTest("Deletion of all contents must be fast", 100, () -> {
      for (int iter = 0; iter < 100000; iter++) {
        ImmutableText another = original.delete(0, original.length());
        assertEquals(0, another.length());
        assertEquals("", another.toString());
      }
    }).assertTiming();
  }

  private static void assertBalanced(CharSequence node) {
    if (node instanceof ImmutableText.CompositeNode) {
      CharSequence head = ((ImmutableText.CompositeNode)node).head;
      CharSequence tail = ((ImmutableText.CompositeNode)node).tail;
      int headLength = head.length();
      int tailLength = tail.length();
      assertTrue("unbalanced: head " + headLength + ", tail " + tailLength,
                          headLength <= tailLength * 2 && tailLength <= headLength * 2);
      assertBalanced(head);
      assertBalanced(tail);
    }
  }

  public void testEquals() {
    ImmutableText i1 = ImmutableText.valueOf("anything");
    ImmutableText i2 = ImmutableText.valueOf("");
    assertFalse(i1.equals(i2));
  }
}