/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import java.util.Arrays;

/**
 * @author dyoma
 */
class IntLCS {
  private final int[] myFirst;
  private final int[] mySecond;

  private final LinkedDiffPaths myPathsMatrix;
  private final int[] myPrevPathKey;
  private int[] myPrevEnds;
  private int[] myCurrentEnds;
  private final int myMaxX;
  private final int myMaxY;

  public IntLCS(int[] first, int[] second) {
    myFirst = first;
    mySecond = second;
    myMaxX = myFirst.length;
    myMaxY = mySecond.length;

    myPathsMatrix = new LinkedDiffPaths(myMaxX, myMaxY);
    myPrevPathKey = new int[myMaxX + myMaxY + 1];
    Arrays.fill(myPrevPathKey, -1);
    myPrevEnds = new int[myMaxX + myMaxY + 1];
    myCurrentEnds = new int[myMaxX + myMaxY + 1];
  }

  public int execute() throws FilesTooBigForDiffException {
    for (int d =0; d <= myMaxX + myMaxY; d++) {
      int minDiag = -calcBound(myMaxY, d);
      int maxDiag = calcBound(myMaxX, d);
      if (d != 0)
        System.arraycopy(myPrevEnds, minDiag + myMaxY, myCurrentEnds, minDiag + myMaxY, maxDiag - minDiag);
       else {
        int end = skipEquals(0, 0);
        if (end > 0) {
          int xy = (end) - 1;
          myPrevPathKey[myMaxY] = myPathsMatrix.encodeStep(xy, xy, end, false, -1);
        }
        if (myMaxX == myMaxY && end == myMaxX) return 0;
        myPrevEnds[myMaxY] = end;
        continue;
      }
      for (int k = minDiag; k <= maxDiag; k += 2) {
        int end;
        if (k == -d) {
          int prevEndV = myPrevEnds[k + 1 + myMaxY];
          int vertical = findDiagonalEnd(k + 1, prevEndV, true);
          end = encodeStep(prevEndV, vertical, k, true);
        } else if (k == d) {
          int prevEndH = myPrevEnds[k - 1 + myMaxY];
          int horisontal = findDiagonalEnd(k - 1, prevEndH, false);
          end = encodeStep(prevEndH, horisontal, k, false);
        } else {
          int prevEndH = myPrevEnds[k - 1 + myMaxY];
          int prevEndV = myPrevEnds[k + 1 + myMaxY];
          if (prevEndH+1 > prevEndV) {
            int horisontal = findDiagonalEnd(k - 1, prevEndH, false);
            end = encodeStep(prevEndH, horisontal, k, false);
          } else {
            int vertical = findDiagonalEnd(k + 1, prevEndV, true);
            end = encodeStep(prevEndV, vertical, k, true);
          }
        }
        myCurrentEnds[k + myMaxY] = end;
        if (k == myMaxX - myMaxY && end == myMaxX) return d;
      }
      int[] temps = myCurrentEnds;
      myCurrentEnds = myPrevEnds;
      myPrevEnds = temps;
    }
    throw new RuntimeException();
  }

  public LinkedDiffPaths getPaths() {
    return myPathsMatrix;
  }

  private int findDiagonalEnd(int prevDiagonal, int prevEnd, boolean isVertical) {
    int x = prevEnd;
    int y = x - prevDiagonal;
    if (isVertical) y++;
    else x++;
    return skipEquals(x, y);
  }

  private int encodeStep(int prevEnd, int diagLength, int tDiagonal, boolean afterVertical) throws FilesTooBigForDiffException {
    int end = prevEnd + diagLength;
    int prevDiagonal = tDiagonal + myMaxY;
    if (!afterVertical) end++;
    if (afterVertical) prevDiagonal++;
    else prevDiagonal--;
    int x = end - 1;
    int y = x - tDiagonal;
    if (x == -1 || y == -1 || x >= myMaxX || y >= myMaxY) return end;
    myPrevPathKey[tDiagonal + myMaxY] = myPathsMatrix.encodeStep(x, y, diagLength, afterVertical, myPrevPathKey[prevDiagonal]);
    return end;
  }

  private int calcBound(int bound, int d) {
    return (d <= bound) ? d : 2 * bound - d;
  }

  private int skipEquals(int x, int y) {
    int skipped = 0;
    while (x < myMaxX && y < myMaxY && myFirst[x] == mySecond[y]) {
      skipped += 1;
      x++;
      y++;
    }
    return skipped;
  }
}
