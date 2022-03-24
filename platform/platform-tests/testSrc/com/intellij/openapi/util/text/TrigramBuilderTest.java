// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.text;

import com.intellij.openapi.util.Ref;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import junit.framework.TestCase;

public class TrigramBuilderTest extends TestCase {
  public void testBuilder() {
    final Ref<Integer> trigramCountRef = new Ref<>();
    final IntList list = new IntArrayList();

    TrigramBuilder.processTrigrams("String$CharData", new TrigramBuilder.TrigramProcessor() {
      @Override
      public boolean test(int value) {
        list.add(value);
        return true;
      }

      @Override
      public boolean consumeTrigramsCount(int count) {
        trigramCountRef.set(count);
        return true;
      }
    });

    list.sort(null);
    Integer trigramCount = trigramCountRef.get();
    assertNotNull(trigramCount);

    int expectedTrigramCount = 13;
    assertEquals(expectedTrigramCount, (int)trigramCount);
    assertEquals(expectedTrigramCount, list.size());

    int[] expected = {buildTrigram("$Ch"), buildTrigram("arD"), buildTrigram("ata"), 6514785, 6578548, 6759523, 6840690, 6909543, 7235364, 7496801, 7498094, 7566450, 7631465, };
    for (int i = 0; i < expectedTrigramCount; ++i) {
      assertEquals(expected[i], list.getInt(i));
    }
  }

  private static int buildTrigram(String s) {
    int tc1 = StringUtil.toLowerCase(s.charAt(0));
    int tc2 = (tc1 << 8) + StringUtil.toLowerCase(s.charAt(1));
    return (tc2 << 8) + StringUtil.toLowerCase(s.charAt(2));
  }
}
