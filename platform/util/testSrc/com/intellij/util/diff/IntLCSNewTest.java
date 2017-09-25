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
package com.intellij.util.diff;

import junit.framework.TestCase;

import java.util.BitSet;

public class IntLCSNewTest extends TestCase {
  public void testEqual() {
    BitSet[] change = buildChange(new int[]{1, 2, 3}, new int[]{1, 2, 3});
    checkChange(change, new int[]{0, 0, 0}, new int[]{0, 0, 0});
  }

  public void testOneAtBegging() {
    BitSet[] change = buildChange(new int[]{1, 2}, new int[]{1, 3});
    checkChange(change, new int[]{0, 1}, new int[]{0, 1});
  }

  public void testOneAntEnd() {
    BitSet[] change = buildChange(new int[]{1, 3}, new int[]{2, 3});
    checkChange(change, new int[]{1, 0}, new int[]{1, 0});
  }

  public void testOneOverAtEnd() {
    BitSet[] change = buildChange(new int[]{1, 2}, new int[]{1, 2, 3});
    checkChange(change, new int[]{0, 0}, new int[]{0, 0, 1});
  }

  public void testOneOverAtBegging() {
    BitSet[] change = buildChange(new int[]{1, 2, 3}, new int[]{2, 3});
    checkChange(change, new int[]{1, 0, 0}, new int[]{0, 0});
  }

  public void testSingleMiddle() {
    BitSet[] change = buildChange(new int[]{1, 2, 3}, new int[]{4, 2, 5});
    checkChange(change, new int[]{1, 0, 1}, new int[]{1, 0, 1});
  }

  public void testAbsolutelyDifferent() {
    BitSet[] change1 = buildChange(new int[]{1, 2}, new int[]{3, 4});
    checkChange(change1, new int[]{1, 1}, new int[]{1, 1});

    BitSet[] change2 = buildChange(new int[]{1, 2, 3}, new int[]{4, 5, 6});
    checkChange(change2, new int[]{1, 1, 1}, new int[]{1, 1, 1});
  }

  public void testSingleUniqueMoved() {
    BitSet[] change = buildChange(new int[]{1, 1, 2, 2, 10}, new int[]{10, 1, 1, 2, 2});
    checkChange(change, new int[]{0, 0, 0, 0, 1}, new int[]{1, 0, 0, 0, 0});
  }

  public void testSingleFunctionMoved() {
    BitSet[] change = buildChange(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 7, 9, 10, 2, 11, 12, 5, 13, 7, 8, 7},
                                  new int[]{10, 2, 11, 12, 5, 13, 7, 8, 7, 9, 1, 2, 3, 4, 5, 6, 7, 8, 7});
    checkChange(change, new int[]{1, 0, 1, 1, 0, 1, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1},
                new int[]{1, 0, 1, 1, 0, 1, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1});
  }

  private static BitSet[] buildChange(int[] first, int[] second) {
    MyersLCS myersLCS = new MyersLCS(first, second);
    myersLCS.execute();
    return myersLCS.getChanges();
  }

  private static void checkChange(BitSet[] change, int[] expected1, int[] expected2) {
    for (int i = 0; i < expected1.length; i++) {
      assertEquals(change[0].get(i), expected1[i] == 1);
    }
    for (int i = 0; i < expected2.length; i++) {
      assertEquals(change[1].get(i), expected2[i] == 1);
    }
  }
}
