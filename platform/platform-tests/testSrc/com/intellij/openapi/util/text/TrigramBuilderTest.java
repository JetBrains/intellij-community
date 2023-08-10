// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.text;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import junit.framework.TestCase;

import java.util.NoSuchElementException;

public class TrigramBuilderTest extends TestCase {
  public void testBuilder() {
    IntList list = new IntArrayList(TrigramBuilder.getTrigrams("String$CharData"));
    list.sort(null);

    int expectedTrigramCount = 13;
    assertEquals(expectedTrigramCount, list.size());

    int[] expected = {buildTrigram("$Ch"), buildTrigram("arD"), buildTrigram("ata"), 6514785, 6578548, 6759523, 6840690, 6909543, 7235364, 7496801, 7498094, 7566450, 7631465, };
    for (int i = 0; i < expectedTrigramCount; ++i) {
      assertEquals(expected[i], list.getInt(i));
    }
  }

  @SuppressWarnings("ConstantConditions")
  public void testIteratorContract() {
    IntIterator iterator = TrigramBuilder.getTrigrams("Str").intIterator();
    assertTrue(iterator.hasNext());
    assertTrue(iterator.hasNext());
    assertTrue(iterator.hasNext());
    assertEquals(7566450, iterator.nextInt());
    assertFalse(iterator.hasNext());
    assertFalse(iterator.hasNext());
    try {
      iterator.nextInt();
      fail();
    } catch (NoSuchElementException ignored) {

    }
  }

  private static int buildTrigram(String s) {
    int tc1 = StringUtil.toLowerCase(s.charAt(0));
    int tc2 = (tc1 << 8) + StringUtil.toLowerCase(s.charAt(1));
    return (tc2 << 8) + StringUtil.toLowerCase(s.charAt(2));
  }
}
