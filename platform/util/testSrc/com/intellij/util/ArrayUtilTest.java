// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class ArrayUtilTest {
  @Test
  public void testInsertString() {
    String[] array = ArrayUtil.EMPTY_STRING_ARRAY;

    array = ArrayUtil.insert(array, 0, "1");
    assertArrayEquals(new String[]{"1"}, array);

    array = ArrayUtil.insert(array, 1, "2");
    assertArrayEquals(new String[]{"1", "2"}, array);

    array = ArrayUtil.insert(array, 0, "3");
    assertArrayEquals(new String[]{"3", "1", "2"}, array);

    array = ArrayUtil.insert(array, 1, "4");
    assertArrayEquals(new String[]{"3", "4", "1", "2"}, array);
  }

  @Test
  public void testInsertInt() {
    int[] array = ArrayUtil.EMPTY_INT_ARRAY;

    array = ArrayUtil.insert(array, 0, 1);
    assertArrayEquals(new int[]{1}, array);

    array = ArrayUtil.insert(array, 1, 2);
    assertArrayEquals(new int[]{1, 2}, array);

    array = ArrayUtil.insert(array, 0, 3);
    assertArrayEquals(new int[]{3, 1, 2}, array);

    array = ArrayUtil.insert(array, 1, 4);
    assertArrayEquals(new int[]{3, 4, 1, 2}, array);
  }

  @Test
  public void testReverse() {
    checkArrayReverse(new int[]{}, new int[]{});
    checkArrayReverse(new int[]{1}, new int[]{1});
    checkArrayReverse(new int[]{1, 2, 3, 4}, new int[]{4, 3, 2, 1});
    checkArrayReverse(new int[]{1, 2, 3, 4, 5}, new int[]{5, 4, 3, 2, 1});
  }

  private static void checkArrayReverse(int[] array, int[] expectedReversedArray) {
    int[] reversed = ArrayUtil.reverseArray(array);
    assertArrayEquals(expectedReversedArray, reversed);
  }
}
