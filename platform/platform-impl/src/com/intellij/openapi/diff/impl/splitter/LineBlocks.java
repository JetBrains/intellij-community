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
import com.intellij.openapi.diff.impl.fragments.LineBlock;
import com.intellij.openapi.diff.impl.fragments.LineFragment;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.incrementalMerge.Change;
import com.intellij.openapi.diff.impl.util.TextDiffTypeEnum;
import com.intellij.util.containers.IntArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;

public class LineBlocks {
  public static final LineBlocks EMPTY = new LineBlocks(SimpleIntervalProvider.EMPTY, new TextDiffTypeEnum[0]);
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.splitter.LineBlocks");
  private final IntervalsProvider myIntervalsProvider;
  private final TextDiffTypeEnum[] myTypes;

  private LineBlocks(IntervalsProvider intervalsProvider, TextDiffTypeEnum[] types) {
    myIntervalsProvider = intervalsProvider;
    myTypes = types;
  }

  public Interval getVisibleIndecies(Trapezium visibleArea) {
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

  public TextDiffTypeEnum getType(int index) {
    return myTypes[index];
  }

  public int getCount() {
    return getBegginings(FragmentSide.SIDE1).length;
  }

  public int[] getBegginings(FragmentSide side) {
    IntArrayList result = new IntArrayList(getIntervals(side).length);
    int previousBeginning = Integer.MIN_VALUE;
    Interval[] sideIntervals = getIntervals(side);
    for (int i = 0; i < sideIntervals.length; i++) {
      int start = sideIntervals[i].getStart();
      if (start != previousBeginning) result.add(start);
      previousBeginning = start;
    }
    return result.toArray();
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
    return myIntervalsProvider.getIntervals(side);
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

  public static LineBlocks fromLineFragments(ArrayList<LineFragment> lines) {
    ArrayList<LineBlock> filtered = new ArrayList<LineBlock>();
    for (Iterator<LineFragment> iterator = lines.iterator(); iterator.hasNext();) {
      LineFragment fragment = iterator.next();
      if (fragment.getType() != null) filtered.add(fragment);
    }
    return createLineBlocks(filtered.toArray(new LineBlock[filtered.size()]));
  }

  static LineBlocks createLineBlocks(LineBlock[] blocks) {
    Arrays.sort(blocks, LineBlock.COMPARATOR);
    Interval[] intervals1 = new Interval[blocks.length];
    Interval[] intervals2 = new Interval[blocks.length];
    TextDiffTypeEnum[] types = new TextDiffTypeEnum[blocks.length];
    for (int i = 0; i < blocks.length; i++) {
      LineBlock block = blocks[i];
      intervals1[i] = new Interval(block.getStartingLine1(), block.getModifiedLines1());
      intervals2[i] = new Interval(block.getStartingLine2(), block.getModifiedLines2());
      types[i] = block.getType();
    }
    return create(intervals1, intervals2, types);
  }

  private static LineBlocks create(Interval[] intervals1, Interval[] intervals2, TextDiffTypeEnum[] types) {
    return new LineBlocks(new SimpleIntervalProvider(intervals1, intervals2), types);
  }

  public static LineBlocks fromChanges(ArrayList<Change> changes) {
    ArrayList<Interval> intervals1 = new ArrayList<Interval>();
    ArrayList<Interval> intervals2 = new ArrayList<Interval>();
    ArrayList<TextDiffTypeEnum> types = new ArrayList<TextDiffTypeEnum>();
    //int prevEnd1 = 0;
    //int prevEnd2 = 0;
    for (Iterator<Change> iterator = changes.iterator(); iterator.hasNext();) {
      Change change = iterator.next();
      if (!change.isValid()) continue;
      int start1 = change.getChangeSide(FragmentSide.SIDE1).getStartLine();
      int start2 = change.getChangeSide(FragmentSide.SIDE2).getStartLine();
      //if (start1 != prevEnd1 || start2 != prevEnd2) {
      //  intervals1.add(Interval.fromTo(prevEnd1, start1));
      //  intervals2.add(Interval.fromTo(prevEnd2, start2));
      //  types.add(null);
      //}
      int end1 = change.getChangeSide(FragmentSide.SIDE1).getEndLine();
      intervals1.add(Interval.fromTo(start1, end1));
      int end2 = change.getChangeSide(FragmentSide.SIDE2).getEndLine();
      intervals2.add(Interval.fromTo(start2, end2));
      types.add(change.getType().getTypeKey().getType());
      //prevEnd1 = end1;
      //prevEnd2 = end2;
    }
    //LOG.assertTrue(prevEnd1 < length1 && prevEnd2 < length2);
    //if (prevEnd1 != length1 || prevEnd2 != length2) {
    //  intervals1.add(Interval.fromTo(prevEnd1, length1));
    //  intervals2.add(Interval.fromTo(prevEnd2, length2));
    //  types.add(null);
    //}

    return create(intervals1.toArray(new Interval[intervals1.size()]),
                  intervals2.toArray(new Interval[intervals2.size()]), types.toArray(new TextDiffTypeEnum[types.size()]));
  }

  public TextDiffTypeEnum[] getTypes() {
    return myTypes;
  }

  public interface IntervalsProvider {
    Interval[] getIntervals(FragmentSide side);
  }
  
  public static class SimpleIntervalProvider implements IntervalsProvider {
    private final Interval[][] myIntervals = new Interval[2][];
    public static final IntervalsProvider EMPTY = new SimpleIntervalProvider(new Interval[0], new Interval[0]);

    public SimpleIntervalProvider(Interval[] intervals1, Interval[] intervals2) {
      myIntervals[0] = intervals1;
      myIntervals[1] = intervals2;
    }

    public Interval[] getIntervals(FragmentSide side) {
      return myIntervals[side.getIndex()];
    }
  }
}
