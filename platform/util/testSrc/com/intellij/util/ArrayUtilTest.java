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
package com.intellij.util;

import junit.framework.TestCase;

/**
 * @author Sergey.Malenkov
 */
public class ArrayUtilTest extends TestCase {

  public void testInsertString() {
    String[] array = ArrayUtil.EMPTY_STRING_ARRAY;

    array = ArrayUtil.insert(array, 0, "1");
    assertEqualsArray(array, "1");

    array = ArrayUtil.insert(array, 1, "2");
    assertEqualsArray(array, "1", "2");

    array = ArrayUtil.insert(array, 0, "3");
    assertEqualsArray(array, "3", "1", "2");

    array = ArrayUtil.insert(array, 1, "4");
    assertEqualsArray(array, "3", "4", "1", "2");
  }

  public void testInsertInt() {
    int[] array = ArrayUtil.EMPTY_INT_ARRAY;

    array = ArrayUtil.insert(array, 0, 1);
    assertEqualsArray(array, 1);

    array = ArrayUtil.insert(array, 1, 2);
    assertEqualsArray(array, 1, 2);

    array = ArrayUtil.insert(array, 0, 3);
    assertEqualsArray(array, 3, 1, 2);

    array = ArrayUtil.insert(array, 1, 4);
    assertEqualsArray(array, 3, 4, 1, 2);
  }

  private static void assertEqualsArray(int[] actual, int... expected) {
    assertEquals(expected.length, actual.length);
    for (int i = 0; i < actual.length; i++) {
      assertEquals(expected[i], actual[i]);
    }
  }

  private static <T> void assertEqualsArray(T[] actual, T... expected) {
    assertEquals(expected.length, actual.length);
    for (int i = 0; i < actual.length; i++) {
      assertEquals(expected[i], actual[i]);
    }
  }
}
