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

package com.intellij.vcs.log.graph.utils;

import com.intellij.vcs.log.graph.utils.impl.CompressedIntList;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CompressedIntListTest {

  private static final int BYTE_MAX = 0x7f;
  private static final int BYTE2_MAX = 0x7fff;
  private static final int BYTE3_MAX = 0x7fffff;
  private static final int INT_MAX = Integer.MAX_VALUE;

  private static String toStr(@NotNull IntList intList) {
    StringBuilder s = new StringBuilder();
    for (int i = 0; i < intList.size(); i++) {
      if (i != 0) s.append(", ");
      s.append(intList.get(i));
    }
    return s.toString();
  }

  private static IntList toDataList(final int... list) {
    return new IntList() {
      @Override
      public int size() {
        return list.length;
      }

      @Override
      public int get(int index) {
        return list[index];
      }
    };
  }

  protected void runTest(int... list) {
    IntList intList = CompressedIntList.newInstance(list, 3);
    String expected = toStr(toDataList(list));
    assertEquals(expected, toStr(intList));
  }

  @Test
  public void empty() {
    runTest();
  }

  @Test
  public void oneValue() {
    runTest(0);
    runTest(BYTE_MAX);
    runTest(BYTE2_MAX);
    runTest(BYTE3_MAX);
  }

  @Test
  public void twoValueWithBigDelta() {
    runTest(0, BYTE_MAX);
    runTest(-10, BYTE_MAX);
    runTest(-BYTE_MAX, BYTE_MAX);

    runTest(0, -BYTE_MAX);
    runTest(0, -BYTE2_MAX);
    runTest(0, -BYTE3_MAX);

    runTest(0, -2 * BYTE3_MAX);
    runTest(0, -2 * BYTE2_MAX);
    runTest(0, -2 * BYTE_MAX);

    runTest(0, BYTE_MAX);
    runTest(0, BYTE2_MAX);
    runTest(0, BYTE3_MAX);

    runTest(0, 2 * BYTE3_MAX);
    runTest(0, 2 * BYTE2_MAX);
    runTest(0, 2 * BYTE_MAX);
  }

  @Test
  public void treeValueWithBigDelta() {
    runTest(BYTE_MAX, 0, BYTE_MAX);
    runTest(BYTE_MAX, -10, BYTE_MAX);
    runTest(BYTE_MAX, -BYTE_MAX, BYTE_MAX);

    runTest(-BYTE_MAX, 0, -BYTE_MAX);
    runTest(-BYTE2_MAX, 0, -BYTE2_MAX);
    runTest(-BYTE3_MAX, 0, -BYTE3_MAX);

    runTest(-2 * BYTE3_MAX, 0, -2 * BYTE3_MAX);
    runTest(-2 * BYTE2_MAX, 0, -2 * BYTE2_MAX);
    runTest(-2 * BYTE_MAX, 0, -2 * BYTE_MAX);

    runTest(BYTE_MAX, 0, BYTE_MAX);
    runTest(BYTE2_MAX, 0, BYTE2_MAX);
    runTest(BYTE3_MAX, 0, BYTE3_MAX);

    runTest(2 * BYTE3_MAX, 0, 2 * BYTE3_MAX);
    runTest(2 * BYTE2_MAX, 0, 2 * BYTE2_MAX);
    runTest(2 * BYTE_MAX, 0, 2 * BYTE_MAX);
  }

  @Test
  public void more() {
    runTest(0, 1, -1, BYTE3_MAX, BYTE3_MAX, BYTE3_MAX, -BYTE3_MAX);
  }

  @Test
  public void bigNumbers() {
    runTest(0, INT_MAX, -INT_MAX, INT_MAX, BYTE3_MAX, 0, -INT_MAX, 0, -INT_MAX, INT_MAX, 0);
  }

  @Test
  public void checkBlockSize() {
    runTest(1);
    runTest(1, 2);
    runTest(1, 2, 3);
    runTest(1, 2, 3, 4);
    runTest(1, 2, 3, 4, 5);
    runTest(1, 2, 3, 4, 5, 6);
    runTest(1, 2, 3, 4, 5, 6, 7);
    runTest(1, 2, 3, 4, 5, 6, 7, 8);
  }
}
