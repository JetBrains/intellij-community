// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.openapi.util.text.StringUtil;
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

  private static void assertBalanced(ImmutableText.Node node) {
    if (node instanceof ImmutableText.CompositeNode) {
      ImmutableText.Node head = ((ImmutableText.CompositeNode)node).head;
      ImmutableText.Node tail = ((ImmutableText.CompositeNode)node).tail;
      int headLength = head.length();
      int tailLength = tail.length();
      assertTrue("unbalanced: head " + headLength + ", tail " + tailLength,
                          headLength <= tailLength * 2 && tailLength <= headLength * 2);
      assertBalanced(head);
      assertBalanced(tail);
    }
  }
}