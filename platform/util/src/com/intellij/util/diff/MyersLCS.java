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

import java.util.BitSet;

/**
 * Algorithm for finding the longest common subsequence of two strings
 * Based on E.W. Myers / An O(ND) Difference Algorithm and Its Variations / 1986
 * O(ND) runtime, O(N) memory
 * <p/>
 * Created by Anton Bannykh
 */
class MyersLCS {
  private final int[] myFirst;
  private final int[] mySecond;

  private final int myStart1;
  private final int myStart2;
  private final int myCount1;
  private final int myCount2;

  private final BitSet myChanges1;
  private final BitSet myChanges2;

  private final int[] VForward;
  private final int[] VBackward;

  public MyersLCS(int[] first, int[] second) {
    this(first, second, 0, first.length, 0, second.length, new BitSet(first.length), new BitSet(second.length));
  }

  public MyersLCS(int[] first, int[] second, int start1, int count1, int start2, int count2, BitSet changes1, BitSet changes2) {
    myFirst = first;
    mySecond = second;
    myStart1 = start1;
    myStart2 = start2;
    myCount1 = count1;
    myCount2 = count2;

    myChanges1 = changes1;
    myChanges2 = changes2;

    myChanges1.set(myStart1, myStart1 + myCount1);
    myChanges2.set(myStart2, myStart2 + myCount2);

    final int totalSequenceLength = myCount1 + myCount2;
    VForward = new int[totalSequenceLength + 1];
    VBackward = new int[totalSequenceLength + 1];
  }

  /**
   * Runs O(ND) Myers algorithm where D is bound by A + B * sqrt(N)
   * <p/>
   * Under certains assumptions about the distribution of the elements of the sequences the expected
   * running time of the myers algorithm is O(N + D^2). Thus under given constraints it reduces to O(N).
   */
  public void executeLinear() {
    try {
      int threshold = 20000 + 10 * (int)Math.sqrt(myCount1 + myCount2);
      execute(threshold, false);
    }
    catch (FilesTooBigForDiffException e) {
      throw new IllegalStateException(e); // should not happen
    }
  }

  public void execute() {
    try {
      execute(myCount1 + myCount2, false);
    }
    catch (FilesTooBigForDiffException e) {
      throw new IllegalStateException(e); // should not happen
    }
  }

  public void executeWithThreshold() throws FilesTooBigForDiffException {
    int threshold = Math.max(20000 + 10 * (int)Math.sqrt(myCount1 + myCount2),
                             FilesTooBigForDiffException.DELTA_THRESHOLD_SIZE);
    execute(threshold, true);
  }

  private void execute(int threshold, boolean throwException) throws FilesTooBigForDiffException {
    if (myCount1 == 0 || myCount2 == 0) return;
    execute(0, myCount1, 0, myCount2, Math.min(threshold, myCount1 + myCount2), throwException);
  }

  //LCS( old[oldStart, oldEnd), new[newStart, newEnd) )
  private void execute(int oldStart, int oldEnd, int newStart, int newEnd, int differenceEstimate,
                       boolean throwException) throws FilesTooBigForDiffException {
    assert oldStart <= oldEnd && newStart <= newEnd;
    if (oldStart < oldEnd && newStart < newEnd) {
      final int oldLength = oldEnd - oldStart;
      final int newLength = newEnd - newStart;
      VForward[newLength + 1] = 0;
      VBackward[newLength + 1] = 0;
      final int halfD = (differenceEstimate + 1) / 2;
      int xx, kk, td;
      xx = kk = td = -1;

      loop:
      for (int d = 0; d <= halfD; ++d) {
        final int L = newLength + Math.max(-d, -newLength + ((d ^ newLength) & 1));
        final int R = newLength + Math.min(d, oldLength - ((d ^ oldLength) & 1));
        for (int k = L; k <= R; k += 2) {
          int x = k == L || k != R && VForward[k - 1] < VForward[k + 1] ? VForward[k + 1] : VForward[k - 1] + 1;
          int y = x - k + newLength;
          x += commonSubsequenceLengthForward(oldStart + x, newStart + y,
                                              Math.min(oldEnd - oldStart - x, newEnd - newStart - y));
          VForward[k] = x;
        }

        if ((oldLength - newLength) % 2 != 0) {
          for (int k = L; k <= R; k += 2) {
            if (oldLength - (d - 1) <= k && k <= oldLength + (d - 1)) {
              if (VForward[k] + VBackward[newLength + oldLength - k] >= oldLength) {
                xx = VForward[k];
                kk = k;
                td = 2 * d - 1;
                break loop;
              }
            }
          }
        }

        for (int k = L; k <= R; k += 2) {
          int x = k == L || k != R && VBackward[k - 1] < VBackward[k + 1] ? VBackward[k + 1] : VBackward[k - 1] + 1;
          int y = x - k + newLength;
          x += commonSubsequenceLengthBackward(oldEnd - 1 - x, newEnd - 1 - y,
                                               Math.min(oldEnd - oldStart - x, newEnd - newStart - y));
          VBackward[k] = x;
        }

        if ((oldLength - newLength) % 2 == 0) {
          for (int k = L; k <= R; k += 2) {
            if (oldLength - d <= k && k <= oldLength + d) {
              if (VForward[oldLength + newLength - k] + VBackward[k] >= oldLength) {
                xx = oldLength - VBackward[k];
                kk = oldLength + newLength - k;
                td = 2 * d;
                break loop;
              }
            }
          }
        }
      }

      if (td > 1) {
        final int yy = xx - kk + newLength;
        final int oldDiff = (td + 1) / 2;
        if (0 < xx && 0 < yy) execute(oldStart, oldStart + xx, newStart, newStart + yy, oldDiff, throwException);
        if (oldStart + xx < oldEnd && newStart + yy < newEnd) execute(oldStart + xx, oldEnd, newStart + yy, newEnd, td - oldDiff, throwException);
      }
      else if (td >= 0) {
        int x = oldStart;
        int y = newStart;
        while (x < oldEnd && y < newEnd) {
          final int commonLength = commonSubsequenceLengthForward(x, y, Math.min(oldEnd - x, newEnd - y));
          if (commonLength > 0) {
            addUnchanged(x, y, commonLength);
            x += commonLength;
            y += commonLength;
          }
          else if (oldEnd - oldStart > newEnd - newStart) {
            ++x;
          }
          else {
            ++y;
          }
        }
      }
      else {
        //The difference is more than the given estimate
        if (throwException) throw new FilesTooBigForDiffException();
      }
    }
  }

  private void addUnchanged(int start1, int start2, int count) {
    myChanges1.set(myStart1 + start1, myStart1 + start1 + count, false);
    myChanges2.set(myStart2 + start2, myStart2 + start2 + count, false);
  }

  private int commonSubsequenceLengthForward(int oldIndex, int newIndex, int maxLength) {
    int x = oldIndex;
    int y = newIndex;

    maxLength = Math.min(maxLength, Math.min(myCount1 - oldIndex, myCount2 - newIndex));
    while (x - oldIndex < maxLength && myFirst[myStart1 + x] == mySecond[myStart2 + y]) {
      ++x;
      ++y;
    }
    return x - oldIndex;
  }

  private int commonSubsequenceLengthBackward(int oldIndex, int newIndex, int maxLength) {
    int x = oldIndex;
    int y = newIndex;

    maxLength = Math.min(maxLength, Math.min(oldIndex, newIndex) + 1);
    while (oldIndex - x < maxLength && myFirst[myStart1 + x] == mySecond[myStart2 + y]) {
      --x;
      --y;
    }
    return oldIndex - x;
  }

  public BitSet[] getChanges() {
    return new BitSet[]{myChanges1, myChanges2};
  }
}
