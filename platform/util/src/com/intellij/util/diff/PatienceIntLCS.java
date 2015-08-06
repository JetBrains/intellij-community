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

import java.util.BitSet;

class PatienceIntLCS {
  private final int[] myFirst;
  private final int[] mySecond;

  private final int myStart1;
  private final int myStart2;
  private final int myCount1;
  private final int myCount2;

  private final BitSet myChanges1;
  private final BitSet myChanges2;

  public PatienceIntLCS(int[] first, int[] second) {
    this(first, second, 0, first.length, 0, second.length, new BitSet(first.length), new BitSet(second.length));
  }

  public PatienceIntLCS(int[] first, int[] second, int start1, int count1, int start2, int count2, BitSet changes1, BitSet changes2) {
    myFirst = first;
    mySecond = second;
    myStart1 = start1;
    myStart2 = start2;
    myCount1 = count1;
    myCount2 = count2;

    myChanges1 = changes1;
    myChanges2 = changes2;
  }

  public void execute() throws FilesTooBigForDiffException {
    execute(false);
  }

  public void execute(boolean failOnSmallReduction) throws FilesTooBigForDiffException {
    execute(myStart1, myCount1, myStart2, myCount2, failOnSmallReduction);
  }

  private void execute(int start1, int count1, int start2, int count2, boolean failOnSmallReduction) throws FilesTooBigForDiffException {
    if (count1 == 0 && count2 == 0) {
      return;
    }

    if (count1 == 0 || count2 == 0) {
      addChange(start1, count1, start2, count2);
      return;
    }

    int startOffset = matchForward(start1, count1, start2, count2);
    start1 += startOffset;
    start2 += startOffset;
    count1 -= startOffset;
    count2 -= startOffset;

    int endOffset = matchBackward(start1, count1, start2, count2);
    count1 -= endOffset;
    count2 -= endOffset;

    if (count1 == 0 || count2 == 0) {
      addChange(start1, count1, start2, count2);
    }
    else {
      UniqueLCS uniqueLCS = new UniqueLCS(myFirst, mySecond, start1, count1, start2, count2);
      int[][] matching = uniqueLCS.execute();

      if (matching == null) {
        checkReduction(count1, count2, failOnSmallReduction);
        IntLCS intLCS = new IntLCS(myFirst, mySecond, start1, count1, start2, count2, myChanges1, myChanges2);
        intLCS.execute();
      }
      else {
        int s1, s2, c1, c2;
        int matched = matching[0].length;
        assert matched > 0;

        c1 = matching[0][0];
        c2 = matching[1][0];

        checkReduction(c1, c2, failOnSmallReduction);
        execute(start1, c1, start2, c2, false);

        for (int i = 1; i < matching[0].length; i++) {
          s1 = matching[0][i - 1] + 1;
          s2 = matching[1][i - 1] + 1;

          c1 = matching[0][i] - s1;
          c2 = matching[1][i] - s2;

          if (c1 > 0 || c2 > 0) {
            checkReduction(c1, c2, failOnSmallReduction);
            execute(start1 + s1, c1, start2 + s2, c2, false);
          }
        }

        if (matching[0][matched - 1] == count1 - 1) {
          s1 = count1 - 1;
          c1 = 0;
        }
        else {
          s1 = matching[0][matched - 1] + 1;
          c1 = count1 - s1;
        }
        if (matching[1][matched - 1] == count2 - 1) {
          s2 = count2 - 1;
          c2 = 0;
        }
        else {
          s2 = matching[1][matched - 1] + 1;
          c2 = count2 - s2;
        }

        checkReduction(c1, c2, failOnSmallReduction);
        execute(start1 + s1, c1, start2 + s2, c2, false);
      }
    }
  }

  private int matchForward(int start1, int count1, int start2, int count2) {
    final int size = Math.min(count1, count2);
    int idx = 0;
    for (int i = 0; i < size; i++) {
      if (!(myFirst[start1 + i] == mySecond[start2 + i])) break;
      ++idx;
    }
    return idx;
  }

  private int matchBackward(int start1, int count1, int start2, int count2) {
    final int size = Math.min(count1, count2);
    int idx = 0;
    for (int i = 1; i <= size; i++) {
      if (!(myFirst[start1 + count1 - i] == mySecond[start2 + count2 - i])) break;
      ++idx;
    }
    return idx;
  }

  private void addChange(int start1, int count1, int start2, int count2) {
    myChanges1.set(start1, start1 + count1);
    myChanges2.set(start2, start2 + count2);
  }

  public BitSet[] getChanges() {
    return new BitSet[]{myChanges1, myChanges2};
  }

  private void checkReduction(int count1, int count2, boolean failOnSmallReduction) throws FilesTooBigForDiffException {
    if (!failOnSmallReduction) return;
    if (count1 * 2 < myCount1) return;
    if (count2 * 2 < myCount2) return;
    throw new FilesTooBigForDiffException(0);
  }
}
