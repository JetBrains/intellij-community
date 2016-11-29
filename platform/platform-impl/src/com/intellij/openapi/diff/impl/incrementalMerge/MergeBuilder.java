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
package com.intellij.openapi.diff.impl.incrementalMerge;

import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.util.ContextLogger;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

class MergeBuilder {
  @NotNull private final ContextLogger LOG;

  @NotNull private final ArrayList<MergeFragment> myResult = new ArrayList<>();

  @NotNull private final int[] myProcessed = new int[]{0, 0, 0}; // LEFT aka SIDE1, RIGHT aka SIDE2, BASE
  @NotNull private final EqualPair[] myPairs = new EqualPair[2]; // LEFT, RIGHT

  public MergeBuilder(@NotNull ContextLogger log) {
    LOG = log;
  }

  /*
   * ASSERT: all unchanged blocks should be squashed
   *   add(B1, V1 ...); | -> B2.getStartOffset() > B1.getEndOffset() || V2.getStartOffset() > V1.getEndOffset()
   *   add(B2, V2 ...); |
   */
  public void add(@NotNull TextRange base, @NotNull TextRange version, @NotNull FragmentSide side) {
    int index = side.getIndex();
    int otherIndex = side.otherSide().getIndex();
    EqualPair pair = new EqualPair(base, version, side);

    LOG.assertTrue(myPairs[index] == null || pair.getBaseStart() - myPairs[index].getBaseEnd() >= 0); // '==' can be in case of insertion
    LOG.assertTrue(myPairs[otherIndex] == null || pair.getBaseStart() >= myPairs[otherIndex].getBaseStart());

    myPairs[index] = pair;
    if (myPairs[otherIndex] != null && myPairs[index].getBaseStart() >= myPairs[otherIndex].getBaseEnd()) myPairs[otherIndex] = null;

    process();
  }

  @NotNull
  public List<MergeFragment> finish(int leftLength, int baseLength, int rightLength) {
    if (!compare(new int[]{leftLength, rightLength, baseLength}, myProcessed)) {
      processConflict(leftLength, baseLength, rightLength);
    }
    return myResult;
  }

  // see "A Formal Investigation of Diff3"
  private void process() {
    while (myPairs[0] != null && myPairs[1] != null) {
      if (myPairs[0].startsFrom(myProcessed) && myPairs[1].startsFrom(myProcessed)) {
        // process stable
        int len = Math.min(myPairs[0].getLength(), myPairs[1].getLength());
        if (!myPairs[0].cutHead(len)) {
          myPairs[0] = null;
        }
        if (!myPairs[1].cutHead(len)) {
          myPairs[1] = null;
        }
        myProcessed[0] += len;
        myProcessed[1] += len;
        myProcessed[2] += len;
      }
      else {
        // process unstable
        int nextBase = Math.max(myPairs[0].getBaseStart(), myPairs[1].getBaseStart());
        int[] nextVersion = new int[2];
        nextVersion[0] = nextBase - myPairs[0].getBaseStart() + myPairs[0].getVersionStart();
        nextVersion[1] = nextBase - myPairs[1].getBaseStart() + myPairs[1].getVersionStart();

        processConflict(nextVersion[0], nextBase, nextVersion[1]);

        if (!myPairs[0].cutHead(nextBase - myPairs[0].getBaseStart())) {
          myPairs[0] = null;
        }
        if (!myPairs[1].cutHead(nextBase - myPairs[1].getBaseStart())) {
          myPairs[1] = null;
        }
      }
    }
  }

  private void processConflict(int nextLeft, int nextBase, int nextRight) {
      addConflict(new TextRange(myProcessed[0], nextLeft),
                  new TextRange(myProcessed[2], nextBase),
                  new TextRange(myProcessed[1], nextRight));

    myProcessed[0] = nextLeft;
    myProcessed[1] = nextRight;
    myProcessed[2] = nextBase;
  }

  private void addConflict(@NotNull TextRange left, @NotNull TextRange base, @NotNull TextRange right) {
    myResult.add(new MergeFragment(left, base, right));
  }

  private static boolean compare(@NotNull int[] lengths, @NotNull int[] processed) {
    for (int i = 0; i < lengths.length; i++) {
      if (lengths[i] != processed[i]) return false;
    }
    return true;
  }

  private class EqualPair {
    private int myBaseStart;
    private int myVersionStart;
    private int myLength;
    private final FragmentSide mySide;

    public EqualPair(@NotNull TextRange base, @NotNull TextRange version, @NotNull FragmentSide side) {
      LOG.assertTrue(base.getLength() == version.getLength());
      LOG.assertTrue(base.getLength() > 0);
      myBaseStart = base.getStartOffset();
      myVersionStart = version.getStartOffset();
      myLength = base.getLength();
      mySide = side;
    }

    public int getBaseStart() {
      return myBaseStart;
    }

    public int getVersionStart() {
      return myVersionStart;
    }

    public int getLength() {
      return myLength;
    }

    public int getBaseEnd() {
      return myBaseStart + myLength;
    }

    @NotNull
    public FragmentSide getSide() {
      return mySide;
    }

    public int getIndex() {
      return mySide.getIndex();
    }

    public boolean startsFrom(int[] bound) {
      return versionStartsFrom(bound) && baseStartFrom(bound);
    }

    public boolean versionStartsFrom(int[] bound) {
      return myVersionStart == bound[mySide.getIndex()];
    }

    public boolean baseStartFrom(int[] bound) {
      return myBaseStart == bound[2];
    }

    public boolean cutHead(int delta) {
      LOG.assertTrue(myLength >= delta);
      LOG.assertTrue(delta >= 0);
      myBaseStart += delta;
      myVersionStart += delta;
      myLength -= delta;
      return myLength > 0;
    }
  }
}