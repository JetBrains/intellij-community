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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

class MergeBuilder {
  private final ContextLogger LOG;
  private final int[] myProcessed = new int[]{0, 0, 0};
  private final ArrayList<MergeFragment> myResult = new ArrayList<MergeFragment>();
  private final EqualPair[] myPairs = new EqualPair[2];

  public MergeBuilder(ContextLogger log) {
    LOG = log;
  }

  public void add(TextRange base, TextRange version, FragmentSide side) {
    LOG.assertTrue(base.getLength() == version.getLength());
    myPairs[side.getIndex()] = new EqualPair(base.getStartOffset(),
                                             version.getStartOffset(), base.getLength(), side, LOG);
    if (myPairs[side.otherSide().getIndex()] == null) return;
    if (myPairs[0].baseStartFrom(myProcessed) && myPairs[1].baseStartFrom(myProcessed)) {
      processInsertOrConflict();
      return;
    }
    if (processNoIntersection()) return;
    for (int i = 0; i < myPairs.length; i++) {
      EqualPair pair = myPairs[i];
      if (pair.startsFrom(myProcessed)) {
        processDeleteOrChange(FragmentSide.fromIndex(i).otherSide());
        return;
      }
    }
    if (!removeSingleSideBase()) return;
    LOG.assertTrue(myPairs[0].getBase() == myPairs[1].getBase());
    LOG.assertTrue(myPairs[0].getBase() > myProcessed[1]);
    addMergeFragment(myPairs[0].processVersion(myProcessed),
                     processBaseChange(myPairs[0]),
                     myPairs[1].processVersion(myProcessed));
    skipProcessed();
  }

  private boolean processNoIntersection() {
    FragmentSide firstSide = getFirstSide();
    int firstIndex = firstSide.getIndex();
    if (myPairs[firstIndex].getBaseEnd() <= myPairs[firstSide.otherSide().getIndex()].getBase()) {
      myPairs[firstIndex] = null;
      return true;
    }
    return false;
  }

  private boolean removeSingleSideBase() {
    FragmentSide firstSide = getFirstSide();
    int firstIndex = firstSide.getIndex();
    int singleSideDelta = myPairs[firstSide.otherSide().getIndex()].getBase() - myPairs[firstIndex].getBase();
    if (singleSideDelta >= myPairs[firstIndex].getLength()) {
      LOG.notTested();
      myPairs[firstIndex] = null;
      return false;
    }
    if (!myPairs[firstIndex].cutHead(singleSideDelta)) {
      LOG.notTested();
      myPairs[firstIndex] = null;
      return false;
    }
    return true;
  }

  private FragmentSide getFirstSide() {
    return myPairs[0].getBase() < myPairs[1].getBase() ? FragmentSide.SIDE1 : FragmentSide.SIDE2;
  }

  private void processDeleteOrChange(FragmentSide side) {
    EqualPair workingPair = myPairs[side.getIndex()];
    EqualPair otherPair = myPairs[side.otherSide().getIndex()];
    LOG.assertTrue(!workingPair.baseStartFrom(myProcessed) &&
                   otherPair.baseStartFrom(myProcessed));
    TextRange versionChange = workingPair.processVersion(myProcessed);
    TextRange baseChange = new TextRange(myProcessed[1], workingPair.getBase());
    int changeLength = workingPair.getBase() - myProcessed[1];
    myProcessed[1] += changeLength;
    myProcessed[otherPair.getSide().getMergeIndex()] += changeLength;
    myProcessed[side.getMergeIndex()] = workingPair.getVersion();
    boolean stillValid = otherPair.cutHead(changeLength);
    LOG.assertTrue(stillValid);
    myResult.add(MergeFragment.notConflict(baseChange, versionChange, side));
    skipProcessed();
  }

  private TextRange processBaseChange(EqualPair workingPair) {
    int base = workingPair.getBase();
    TextRange change = new TextRange(myProcessed[1], base);
    int otherBase = myPairs[workingPair.getSide().otherSide().getIndex()].getBase();
    LOG.assertTrue(otherBase >= base);
    myProcessed[1] = base;
    return change;
  }

  private void processInsertOrConflict() {
    boolean leftVersionProcessed = myPairs[0].versionStartsFrom(myProcessed);
    boolean rightVersionProcessed = myPairs[1].versionStartsFrom(myProcessed);
    TextRange emptyBase = new TextRange(myProcessed[1], myProcessed[1]);
    if (!leftVersionProcessed && rightVersionProcessed) {
      addMergeFragment(myPairs[0].processVersion(myProcessed), emptyBase, null);
    } else if (!rightVersionProcessed && leftVersionProcessed) {
      addMergeFragment(null, emptyBase, myPairs[1].processVersion(myProcessed));
    } else  if (!leftVersionProcessed && !rightVersionProcessed) {
      addMergeFragment(myPairs[0].processVersion(myProcessed), emptyBase, myPairs[1].processVersion(myProcessed));
    }
    skipProcessed();
  }

  private void addMergeFragment(@Nullable TextRange left, TextRange base, @Nullable TextRange right) {
    myResult.add(new MergeFragment(left, base, right));
  }

  private void skipProcessed() {
    LOG.assertTrue(myProcessed[0] == myPairs[0].getVersion());
    LOG.assertTrue(myProcessed[2] == myPairs[1].getVersion());
    LOG.assertTrue(myPairs[0].getBase() == myPairs[1].getBase());
    LOG.assertTrue(myPairs[0].getBase() == myProcessed[1]);
    int processedDelta = Math.min(myPairs[0].getBaseEnd(), myPairs[1].getBaseEnd()) - myProcessed[1];
    LOG.assertTrue(processedDelta > 0);
    for (int i = 0; i < myProcessed.length; i++) myProcessed[i] += processedDelta;
    for (int i = 0; i < myPairs.length; i++)
      if (!myPairs[i].cutHead(processedDelta)) myPairs[i] = null;
    LOG.assertTrue(myPairs[0] == null || myPairs[1] == null);
  }

  public List<MergeFragment> finish(int leftLength, int baseLength, int rightLength) {
    int[] lengths = new int[]{ leftLength, baseLength, rightLength };
    if (isProcessedUpto(lengths)) {
      return myResult;
    }
    int[] afterEnds = new int[3];
    for (int i = 0; i < lengths.length; i++) {
      afterEnds[i] = lengths[i] + 1;
    }
    FragmentSide notProcessedSide = getNotProcessedSide();
    if (notProcessedSide == null) {
      addTailChange(lengths);
      return myResult;
    }
    myPairs[notProcessedSide.getIndex()].grow(1);
    FragmentSide processedSide = notProcessedSide.otherSide();
    add(createRange(lengths, afterEnds, 1), createRange(lengths, afterEnds, processedSide.getMergeIndex()), processedSide);
    if (!isProcessedUpto(afterEnds)) {
      add(createRange(lengths, afterEnds, 1), createRange(lengths, afterEnds, notProcessedSide.getMergeIndex()), notProcessedSide);
    }
    LOG.assertTrue(isProcessedUpto(afterEnds));
    return myResult;
  }

  private static TextRange createRange(int[] starts, int[] ends, int column) {
    return new TextRange(starts[column], ends[column]);
  }

  private void addTailChange(int[] lengths) {
    TextRange[] tailChange = new TextRange[3];
    for (int i = 0; i < tailChange.length; i++) {
      if (i != 1 && myProcessed[i] == lengths[i]) tailChange[i] = null;
      else tailChange[i] = new TextRange(myProcessed[i], lengths[i]);
    }
    myResult.add(new MergeFragment(tailChange[0], tailChange[1], tailChange[2]));
  }

  @Nullable
  private FragmentSide getNotProcessedSide() {
    EqualPair left = myPairs[0];
    EqualPair right = myPairs[1];
    LOG.assertTrue(left == null || right == null);
    if (left != null) return FragmentSide.SIDE1;
    if (right != null) return FragmentSide.SIDE2;
    return null;
  }

  private boolean isProcessedUpto(int[] lengths) {
    for (int i = 0; i < lengths.length; i++) {
      int length = lengths[i];
      if (length != myProcessed[i]) return false;
    }
    return true;
  }

  private static class EqualPair {
    private final ContextLogger LOG;
    private int myVersionStart;
    private int myBaseStart;
    private int myLength;
    private final FragmentSide mySide;

    public EqualPair(int baseStart, int versionStart, int length, FragmentSide side, ContextLogger log) {
      LOG = log;
      myBaseStart = baseStart;
      myVersionStart = versionStart;
      myLength = length;
      mySide = side;
    }

    public boolean startsFrom(int[] bound) {
      return versionStartsFrom(bound) && baseStartFrom(bound);
    }

    public boolean versionStartsFrom(int[] bound) {
      return myVersionStart == bound[mySide.getMergeIndex()];
    }

    public boolean baseStartFrom(int[] bound) {
      return myBaseStart == bound[1];
    }

    public int getBaseEnd() {
      return myBaseStart + myLength;
    }

    public int getVersion() {
      return myVersionStart;
    }

    public TextRange processVersion(int[] processed) {
      TextRange change = new TextRange(processed[mySide.getMergeIndex()], getVersion());
      processed[mySide.getMergeIndex()] = getVersion();
      return change;
    }

    public boolean cutHead(int processedDelta) {
      LOG.assertTrue(myLength >= processedDelta);
      myBaseStart += processedDelta;
      myVersionStart += processedDelta;
      myLength -= processedDelta;
      return myLength > 0;
    }

    public int getBase() {
      return myBaseStart;
    }

    public int getLength() {
      return myLength;
    }

    public FragmentSide getSide() {
      return mySide;
    }

    public void grow(int delta) {
      myLength += delta;
    }
  }
}
