/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.Random;

public class IntLCSAutoTest extends TestCase {
  private final Random RNG = new Random();

  private static final int ITERATIONS = 1000;
  private static final int MAX_LENGTH = 300;
  private static final int CHAR_COUNT = 20;

  private enum Type {MyersLCS, PatienceLCS}

  public void testMyersLCS() throws Exception {
    for (int i = 0; i < ITERATIONS; i++) {
      doTestLCS(MAX_LENGTH, CHAR_COUNT, Type.MyersLCS);
    }
  }

  public void testPatienceLCS() throws Exception {
    for (int i = 0; i < ITERATIONS; i++) {
      doTestLCS(MAX_LENGTH, CHAR_COUNT, Type.PatienceLCS);
    }
  }

  private void doTestLCS(int maxLength, int charCount, @NotNull Type type) throws FilesTooBigForDiffException {
    int[] sequence1 = generateSequence(maxLength, charCount);
    int[] sequence2 = generateSequence(maxLength, charCount);

    int start1 = RNG.nextInt(sequence1.length);
    int start2 = RNG.nextInt(sequence2.length);
    int count1 = RNG.nextInt(sequence1.length - start1);
    int count2 = RNG.nextInt(sequence2.length - start2);

    BitSet changes1 = new BitSet(sequence1.length);
    BitSet changes2 = new BitSet(sequence2.length);

    switch (type) {
      case MyersLCS:
        MyersLCS myersLCS = new MyersLCS(sequence1, sequence2, start1, count1, start2, count2, changes1, changes2);
        myersLCS.execute();
        break;
      case PatienceLCS:
        PatienceIntLCS patienceLCS = new PatienceIntLCS(sequence1, sequence2, start1, count1, start2, count2, changes1, changes2);
        patienceLCS.execute();
        break;
    }

    verifyLCS(sequence1, sequence2, start1, count1, start2, count2, changes1, changes2);
  }

  public static void verifyLCS(@NotNull int[] sequence1, @NotNull int[] sequence2,
                               @NotNull BitSet changes1, @NotNull BitSet changes2) {
    verifyLCS(sequence1, sequence2, 0, sequence1.length, 0, sequence2.length, changes1, changes2);
  }

  private static void verifyLCS(@NotNull int[] sequence1, @NotNull int[] sequence2,
                                int start1, int count1, int start2, int count2,
                                @NotNull BitSet changes1, @NotNull BitSet changes2) {
    int index1 = changes1.nextClearBit(start1);
    int index2 = changes2.nextClearBit(start2);

    while (index1 < start1 + count1 || index2 < start2 + count2) {
      assertTrue(index1 < start1 + count1);
      assertTrue(index2 < start2 + count2);

      assertEquals(sequence1[index1], sequence2[index2]);

      index1 = changes1.nextClearBit(index1 + 1);
      index2 = changes2.nextClearBit(index2 + 1);
    }
    assertTrue(index1 >= start1 + count1 && index2 >= start2 + count2);
  }

  @NotNull
  private int[] generateSequence(int maxLength, int charCount) {
    int[] result = new int[RNG.nextInt(maxLength / 2) + maxLength / 2];
    for (int i = 0; i < result.length; i++) {
      result[i] = RNG.nextInt(charCount);
    }
    return result;
  }
}
