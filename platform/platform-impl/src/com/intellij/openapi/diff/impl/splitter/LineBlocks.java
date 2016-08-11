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
package com.intellij.openapi.diff.impl.splitter;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.diff.impl.fragments.LineBlock;
import com.intellij.openapi.diff.impl.fragments.LineFragment;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.incrementalMerge.Change;
import com.intellij.openapi.diff.impl.incrementalMerge.ChangeList;
import com.intellij.openapi.diff.impl.util.TextDiffType;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class LineBlocks {
  public static final LineBlocks EMPTY = new LineBlocks(Collections.<Diff>emptyList());
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.splitter.LineBlocks");
  private final List<Diff> myDiffs;

  private LineBlocks(List<Diff> diffs) {
    myDiffs = diffs;
  }

  public Interval getVisibleIndices(Trapezium visibleArea) {
    Interval visible1 = visibleArea.getBase1();
    Interval visible2 = visibleArea.getBase2();
    Interval[] intervals1 = getIntervals(FragmentSide.SIDE1);
    Interval[] intervals2 = getIntervals(FragmentSide.SIDE2);
    int index = Math.min(getMaxStartedIndex(intervals1, visible1.getStart()),
                         getMaxStartedIndex(intervals2, visible2.getStart()));
    int lastIndex = Math.max(getMinNotStartedIndex(intervals1, visible1.getEnd()),
                             getMinNotStartedIndex(intervals2, visible2.getEnd()));
    return Interval.fromTo(index, lastIndex);
  }

  public Trapezium getTrapezium(int index) {
    return new Trapezium(getIntervals(FragmentSide.SIDE1)[index],
                         getIntervals(FragmentSide.SIDE2)[index]);
  }

  public TextDiffType getType(int index) {
    return myDiffs.get(index).getDiffType();
  }

  public int getCount() {
    return getBeginnings(FragmentSide.SIDE1).length;
  }

  /**
   * Get beginnings of all the changes from the specified side.
   * @param side Side of the changes.
   */
  public int[] getBeginnings(FragmentSide side) {
    return getBeginnings(side, false);
  }

  /**
   * Get beginnings of the changes from the specified side.
   * @param side          Side of the changes.
   * @param unappliedOnly If true - only unapplied changes will be considered, if false - all changes (both applied and not applied).
   */
  public int[] getBeginnings(FragmentSide side, boolean unappliedOnly) {
    List<Integer> result = new ArrayList<>(myDiffs.size());
    int previousBeginning = Integer.MIN_VALUE;

    for (Diff diff : myDiffs) {
      if (!unappliedOnly || !diff.getDiffType().isApplied()) {
        Interval interval = diff.getInterval(side);
        int start = interval.getStart();
        if (start != previousBeginning) result.add(start);
        previousBeginning = start;
      }
    }

    return ArrayUtil.toIntArray(result);
  }

  static int getMaxStartedIndex(Interval[] intervals, int start) {
    int index = getMinIndex(intervals, start, Interval.START_COMPARATOR);
    for (int i = index; i > 0; i--)
      if (intervals[i-1].getEnd() <= start) return i;
    return 0;
  }

  static int getMinNotStartedIndex(Interval[] intervals, int end) {
    int index = getMinIndex(intervals, end, Interval.END_COMPARATOR);
    for (int i = index; i < intervals.length; i++)
      if (intervals[i].getStart() >= end) return i;
    return intervals.length;
  }

  private static int getMinIndex(Interval[] intervals, int start, Comparator comparator) {
    int index = Arrays.binarySearch(intervals, new Integer(start), comparator);
    return index >= 0 ? index : -index - 1;
  }

  public int transform(FragmentSide masterSide, int location) {
    return transform(location, getIntervals(masterSide), getIntervals(masterSide.otherSide()));
  }

  public Interval[] getIntervals(FragmentSide side) {
    Interval[] intervals = new Interval[myDiffs.size()];
    for (int i = 0; i < intervals.length; i++) {
      intervals[i] = myDiffs.get(i).getInterval(side);
    }
    return intervals;
  }

  public TextDiffType[] getTypes() {
    TextDiffType[] types = new TextDiffType[myDiffs.size()];
    for (int i = 0; i < types.length; i++) {
      types[i] = myDiffs.get(i).getDiffType();
    }
    return types;
  }

  private int transform(int location, Interval[] domain, Interval[] range) {
    if (domain.length == 0) {
      if (range.length != 0) {
        LOG.error("" + range.length);
      }
      return location;
    }
    int count = getIntervals(FragmentSide.SIDE1).length;
    LOG.assertTrue(count == getIntervals(FragmentSide.SIDE2).length);
    int index = getMaxStartedIndex(domain, location);
    Interval leftInterval;
    Interval rightInterval;
    if (index == 0) {
      if (domain[0].contains(location)) {
        leftInterval = domain[0];
        rightInterval = range[0];
      } else {
        leftInterval = Interval.fromTo(0, domain[0].getStart());
        rightInterval = Interval.fromTo(0, range[0].getStart());
      }
    } else if (index == count) {
      leftInterval = Interval.toInf(domain[count - 1].getEnd());
      rightInterval = Interval.toInf(range[count - 1].getEnd());
    } else {
      if (domain[index].contains(location)) {
        leftInterval = domain[index];
        rightInterval = range[index];
      } else {
        leftInterval = Interval.fromTo(domain[index - 1].getEnd(), domain[index].getStart());
        rightInterval = Interval.fromTo(range[index - 1].getEnd(), range[index].getStart());
      }
    }
    return LinearTransformation.oneToOne(location, leftInterval.getStart(), rightInterval);
  }

  public static LineBlocks fromLineFragments(List<LineFragment> lines) {
    ArrayList<LineBlock> filtered = new ArrayList<>();
    for (LineFragment fragment : lines) {
      if (fragment.getType() != null) filtered.add(fragment);
    }
    return createLineBlocks(filtered.toArray(new LineBlock[filtered.size()]));
  }

  static LineBlocks createLineBlocks(LineBlock[] blocks) {
    Arrays.sort(blocks, LineBlock.COMPARATOR);
    List<Diff> diffs = new ArrayList<>(blocks.length);
    for (LineBlock block : blocks) {
      Interval interval1 = new Interval(block.getStartingLine1(), block.getModifiedLines1());
      Interval interval2 = new Interval(block.getStartingLine2(), block.getModifiedLines2());
      diffs.add(new Diff(interval1, interval2, makeTextDiffType(block)));
    }
    return new LineBlocks(diffs);
  }

  private static TextDiffType makeTextDiffType(LineBlock block) {
    TextDiffType type = TextDiffType.create(block.getType());
    if (block instanceof LineFragment) {
      return DiffUtil.makeTextDiffType((LineFragment)block);
    }
    return type;
  }

  @NotNull
  public static LineBlocks fromChanges(@NotNull List<Change> changes) {
    // changes may come mixed, need to sort them to get correct intervals
    Collections.sort(changes, ChangeList.CHANGE_ORDER);

    List<Diff> diffs = new ArrayList<>(changes.size());
    for (Change change : changes) {
      if (!change.isValid()) { continue; }
      int start1 = change.getChangeSide(FragmentSide.SIDE1).getStartLine();
      int end1 = change.getChangeSide(FragmentSide.SIDE1).getEndLine();
      Interval interval1 = Interval.fromTo(start1, end1);

      int start2 = change.getChangeSide(FragmentSide.SIDE2).getStartLine();
      int end2 = change.getChangeSide(FragmentSide.SIDE2).getEndLine();
      Interval interval2 = Interval.fromTo(start2, end2);

      diffs.add(new Diff(interval1, interval2, change.getType().getTypeKey()));
    }
    return new LineBlocks(diffs);
  }

  private static class Diff {

    @NotNull private final Interval myIntervalForSide1;
    @NotNull private final Interval myIntervalForSide2;
    @NotNull private final TextDiffType myDiffType;

    private Diff(@NotNull Interval intervalForSide1, @NotNull Interval intervalForSide2, @NotNull TextDiffType type) {
      myIntervalForSide1 = intervalForSide1;
      myIntervalForSide2 = intervalForSide2;
      myDiffType = type;
    }

    @NotNull
    public TextDiffType getDiffType() {
      return myDiffType;
    }

    public Interval getInterval(FragmentSide side) {
      return side == FragmentSide.SIDE1 ? myIntervalForSide1 : myIntervalForSide2;
    }
  }
}
