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

public class PatienceIntLCSTest extends TestCase {
  public void testEqual() throws FilesTooBigForDiffException {
    BitSet[] change = buildChange(new int[]{1, 2, 3}, new int[]{1, 2, 3});
    checkChange(change, new int[]{0, 0, 0}, new int[]{0, 0, 0});
  }

  public void testOneAtBegging() throws FilesTooBigForDiffException {
    BitSet[] change = buildChange(new int[]{1, 2}, new int[]{1, 3});
    checkChange(change, new int[]{0, 1}, new int[]{0, 1});
  }

  public void testOneAntEnd() throws FilesTooBigForDiffException {
    BitSet[] change = buildChange(new int[]{1, 3}, new int[]{2, 3});
    checkChange(change, new int[]{1, 0}, new int[]{1, 0});
  }

  public void testOneOverAtEnd() throws FilesTooBigForDiffException {
    BitSet[] change = buildChange(new int[]{1, 2}, new int[]{1, 2, 3});
    checkChange(change, new int[]{0, 0}, new int[]{0, 0, 1});
  }

  public void testOneOverAtBegging() throws FilesTooBigForDiffException {
    BitSet[] change = buildChange(new int[]{1, 2, 3}, new int[]{2, 3});
    checkChange(change, new int[]{1, 0, 0}, new int[]{0, 0});
  }

  public void testSingleMiddle() throws FilesTooBigForDiffException {
    BitSet[] change = buildChange(new int[]{1, 2, 3}, new int[]{4, 2, 5});
    checkChange(change, new int[]{1, 0, 1}, new int[]{1, 0, 1});
  }

  public void testAbsolutelyDifferent() throws FilesTooBigForDiffException {
    BitSet[] change1 = buildChange(new int[]{1, 2}, new int[]{3, 4});
    checkChange(change1, new int[]{1, 1}, new int[]{1, 1});

    BitSet[] change2 = buildChange(new int[]{1, 2, 3}, new int[]{4, 5, 6});
    checkChange(change2, new int[]{1, 1, 1}, new int[]{1, 1, 1});
  }

  public void testSingleUniqueMoved() throws FilesTooBigForDiffException {
    BitSet[] change = buildChange(new int[]{1, 1, 2, 2, 10}, new int[]{10, 1, 1, 2, 2});
    checkChange(change, new int[]{1, 1, 1, 1, 0}, new int[]{0, 1, 1, 1, 1});
  }

  public void testSingleFunctionMoved() throws FilesTooBigForDiffException {
    BitSet[] change = buildChange(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 7, 9, 10, 2, 11, 12, 5, 13, 7, 8, 7},
                                  new int[]{10, 2, 11, 12, 5, 13, 7, 8, 7, 9, 1, 2, 3, 4, 5, 6, 7, 8, 7});
    checkChange(change, new int[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0},
                new int[]{0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1});
  }

  public void testBug1() throws FilesTooBigForDiffException {
    BitSet[] change = buildChange(new int[]{2, 3, 4, 6, 7, 8, 9, 11, 12, 4, 6, 11, 15},
                                  new int[]{2, 3, 6, 7, 8, 9, 6, 11, 12, 4, 11, 15, 6});
    checkChange(change, new int[]{0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0},
                new int[]{0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0});
  }

  public void testInnerChunks1() throws FilesTooBigForDiffException {
    BitSet[] change = buildChange(new int[]{0, 2, 0, 3, 0, 4, 0, 5, 0, 6, 0, 7, 0}, new int[]{1, 2, 1, 3, 1, 4, 1, 5, 1, 6, 1, 7, 1});
    checkChange(change, new int[]{1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1}, new int[]{1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1});
  }

  public void testInnerChunks2() throws FilesTooBigForDiffException {
    BitSet[] change = buildChange(new int[]{0, 2, 3, 0, 4, 5, 0}, new int[]{1, 2, 1, 3, 4, 5, 1});
    checkChange(change, new int[]{1, 0, 0, 1, 0, 0, 1}, new int[]{1, 0, 1, 0, 0, 0, 1});
  }

  public void testInnerChunks3() throws FilesTooBigForDiffException {
    BitSet[] change = buildChange(new int[]{15, 1, 2, 3, 1, 4, 5, 1, 15}, new int[]{13, 1, 2, 1, 3, 4, 5, 1, 13});
    checkChange(change, new int[]{1, 0, 0, 0, 1, 0, 0, 0, 1}, new int[]{1, 0, 0, 1, 0, 0, 0, 0, 1});
  }

  public void testInnerChunks4() throws FilesTooBigForDiffException {
    BitSet[] change = buildChange(new int[]{15, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6, 0, 7, 0, 15},
                                  new int[]{13, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6, 0, 7, 0, 13});
    checkChange(change, new int[]{1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}, new int[]{1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1});
  }

  public void testNotAbortedEqual() {
    checkAborted(false, new int[]{1, 2, 3}, new int[]{1, 2, 3});
  }

  public void testNotAbortedSimple() {
    checkAborted(false, new int[]{100, 1, 2, 3, 4, 5, 100}, new int[]{1, 2, 3, 4});
  }

  public void testAbortedNoMatches() {
    checkAborted(true, new int[]{4, 1, 2, 3}, new int[]{500, 100, 600,});
  }

  public void testAbortedSimple() {
    checkAborted(true, new int[]{4, 1, 2, 3, 100}, new int[]{5, 1, 6, 8, 9, 10});
  }

  public void testNotAbortedSecondStep() {
    checkAborted(false,
                 new int[]{100, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 100},
                 new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0});
  }

  public void testNotAbortedThirdStep() {
    checkAborted(false,
                 new int[]{100, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 200, 1, 0, 100},
                 new int[]{2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 2});
  }

  public void testAbortedForthStep() {
    checkAborted(true,
                 new int[]{100, 3, 2, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 200, 1, 0, 100},
                 new int[]{2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 1, 0, 2});
  }

  private static BitSet[] buildChange(int[] first, int[] second) throws FilesTooBigForDiffException {
    PatienceIntLCS patienceIntLCS = new PatienceIntLCS(first, second);
    patienceIntLCS.execute();
    return patienceIntLCS.getChanges();
  }

  private static void checkChange(BitSet[] change, int[] expected1, int[] expected2) {
    for (int i = 0; i < expected1.length; i++) {
      assertEquals(change[0].get(i), expected1[i] == 1);
    }
    for (int i = 0; i < expected2.length; i++) {
      assertEquals(change[1].get(i), expected2[i] == 1);
    }
  }

  private static void checkAborted(boolean shouldBeAborted, int[] first, int[] second) {
    PatienceIntLCS patienceIntLCS = new PatienceIntLCS(first, second);
    try {
      patienceIntLCS.execute(true);
      assertFalse(shouldBeAborted);
    }
    catch (FilesTooBigForDiffException e) {
      assertTrue(shouldBeAborted);
    }
  }
}
