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

public class UniqueLCSTest extends TestCase {
  public void testNoUnique() throws FilesTooBigForDiffException {
    int[][] change = buildChange(new int[]{1, 2, 3}, new int[]{4, 5, 6});
    assertNull(change);

    change = buildChange(new int[]{1, 2, 1}, new int[]{1, 3, 1});
    assertNull(change);

    change = buildChange(new int[]{1, 2, 3, 3, 2, 1}, new int[]{1, 2, 3});
    assertNull(change);

    change = buildChange(new int[]{1, 2, 3}, new int[]{1, 2, 3, 3, 2, 1});
    assertNull(change);

    change = buildChange(new int[]{1, 2, 3}, new int[]{1, 2, 3, 3, 2, 1});
    assertNull(change);

    change = buildChange(new int[]{1, 2, 1}, new int[]{2, 1, 2});
    assertNull(change);

    change = buildChange(new int[]{}, new int[]{2, 1, 2});
    assertNull(change);

    change = buildChange(new int[]{1, 2, 2}, new int[]{});
    assertNull(change);

    change = buildChange(new int[]{}, new int[]{});
    assertNull(change);
  }

  public void testSingleUnique() throws FilesTooBigForDiffException {
    int[][] change = buildChange(new int[]{1}, new int[]{1});
    checkChange(change, new int[]{0}, new int[]{0});

    change = buildChange(new int[]{5, 1}, new int[]{1});
    checkChange(change, new int[]{1}, new int[]{0});

    change = buildChange(new int[]{1}, new int[]{2, 1});
    checkChange(change, new int[]{0}, new int[]{1});

    change = buildChange(new int[]{1}, new int[]{1, 4});
    checkChange(change, new int[]{0}, new int[]{0});

    change = buildChange(new int[]{1, 3}, new int[]{1});
    checkChange(change, new int[]{0}, new int[]{0});

    change = buildChange(new int[]{5, 1, 3}, new int[]{2, 1, 4});
    checkChange(change, new int[]{1}, new int[]{1});
  }

  public void testSingleSequence() throws FilesTooBigForDiffException {
    int[][] change = buildChange(new int[]{2, 4, 6, 1, 8, 3, 8, 2, 6, 5, 2, 4, 7, 11, 13}, new int[]{1, 10, 2, 3, 5, 7, 11, 13, 12});
    checkChange(change, new int[]{3, 5, 9, 12, 13, 14}, new int[]{0, 3, 4, 5, 6, 7});
  }

  public void testLargestLCS1() throws FilesTooBigForDiffException {
    checkMaxSequence(new int[]{1, 2, 3}, new int[]{1, 2, 3});
  }

  public void testLargestLCS2() throws FilesTooBigForDiffException {
    checkMaxSequence(new int[]{5, 6, 1, 2, 3}, new int[]{1, 2, 3});
  }

  public void testLargestLCS3() throws FilesTooBigForDiffException {
    checkMaxSequence(new int[]{0, 8, 4, 12, 2, 10, 6, 14, 1, 9, 5, 13, 3, 7, 15}, new int[]{0, 2, 6, 9, 13, 15});
  }

  public void testLargestLCS4() throws FilesTooBigForDiffException {
    checkMaxSequence(new int[]{1, 9, 3, 8, 11, 4, 5, 6, 19, 7}, new int[]{1, 3, 4, 5, 6, 7});
  }

  private static int[][] buildChange(int[] first, int[] second) throws FilesTooBigForDiffException {
    UniqueLCS uniqueLCS = new UniqueLCS(first, second);
    return uniqueLCS.execute();
  }

  private static void checkChange(int[][] change, int[] expected1, int[] expected2) {
    for (int i = 0; i < expected1.length; i++) {
      assertEquals(change[0][i], expected1[i]);
    }
    for (int i = 0; i < expected2.length; i++) {
      assertEquals(change[1][i], expected2[i]);
    }
  }

  private static void checkMaxSequence(int[] sequence, int[] expected) throws FilesTooBigForDiffException {
    int max = 0;
    for (int i = 0; i < sequence.length; i++) {
      max = Math.max(sequence[i] + 1, max);
    }

    int[] first = new int[sequence.length];
    int[] second = new int[max + 2];

    for (int i = 0; i < sequence.length; i++) {
      assertEquals("Elements in sequence should be unique", second[sequence[i]], 0);
      first[i] = i + 1;
      second[sequence[i]] = i + 1;
    }

    int[][] result = buildChange(first, second);

    assertEquals(result[0].length, result[1].length);
    assertEquals(result[0].length, expected.length);
    for (int i = 0; i < expected.length; i++) {
      assertEquals(expected[i], sequence[result[0][i]]);
    }
  }
}
