package com.intellij.openapi.diff.impl.splitter;

import com.intellij.openapi.diff.impl.fragments.LineBlock;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import junit.framework.TestCase;

public class LineBlocksTest extends TestCase {
  public void testVisibles() {
    LineBlocks lineBlocks = LineBlocks.createLineBlocks(new LineBlock[]{new LineBlock(0, 1, 2, 1, null), new LineBlock(2, 1, 4, 1, null)});
    Interval indecies = lineBlocks.getVisibleIndices(new Trapezium(1, 1, 2, 1));
    assertEquals(Interval.fromTo(0, 1), indecies);
    assertEquals(new Trapezium(0, 1, 2, 1), lineBlocks.getTrapezium(indecies.getStart()));

    indecies = lineBlocks.getVisibleIndices(new Trapezium(4, 2, 4, 3));
    assertEquals(Interval.fromTo(1, 2), indecies);
    assertEquals(new Trapezium(2, 1, 4, 1), lineBlocks.getTrapezium(indecies.getStart()));

    indecies = lineBlocks.getVisibleIndices(new Trapezium(3, 1, 3, 1));
    assertEquals(Interval.fromTo(1, 2), indecies);
    assertEquals(new Trapezium(2, 1, 4, 1), lineBlocks.getTrapezium(indecies.getStart()));
  }

  public void testIndecies() {
    Interval[] intervals = new Interval[]{new Interval(1, 2), new Interval(4, 2), new Interval(6, 2)};

    assertEquals(0, LineBlocks.getMaxStartedIndex(intervals, 0));
    assertEquals(0, LineBlocks.getMaxStartedIndex(intervals, 1));
    assertEquals(0, LineBlocks.getMaxStartedIndex(intervals, 2));
    assertEquals(1, LineBlocks.getMaxStartedIndex(intervals, 3));
    assertEquals(1, LineBlocks.getMaxStartedIndex(intervals, 4));

    assertEquals(1, LineBlocks.getMinNotStartedIndex(intervals, 2));
    assertEquals(1, LineBlocks.getMinNotStartedIndex(intervals, 3));
    assertEquals(2, LineBlocks.getMinNotStartedIndex(intervals, 6));
    assertEquals(3, LineBlocks.getMinNotStartedIndex(intervals, 7));
  }

  public void testLineNumberTransformation() {
    LineBlocks lineBlocks = LineBlocks.createLineBlocks(new LineBlock[]{
        new LineBlock(2, 2, 2, 2, null),
        new LineBlock(6, 1, 6, 2, null),
        new LineBlock(8, 2, 9, 0, null)});

    checkLeftRight(lineBlocks, 0, 0);
    checkLeftRight(lineBlocks, 3, 3);
    checkLeftRight(lineBlocks, 5, 5);
    checkLeftRight(lineBlocks, 6, 6);
    assertEquals(6, lineBlocks.transform(FragmentSide.SIDE2, 7));
    checkLeftRight(lineBlocks, 7, 8);
    checkLeftRight(lineBlocks, 8, 9);
    assertEquals(9, lineBlocks.transform(FragmentSide.SIDE1, 9));
    assertEquals(9, lineBlocks.transform(FragmentSide.SIDE1, 10));
    checkLeftRight(lineBlocks, 11, 10);
  }

  private void checkLeftRight(LineBlocks lineBlocks, int left, int right) {
    assertEquals(right, lineBlocks.transform(FragmentSide.SIDE1, left));
    assertEquals(left, lineBlocks.transform(FragmentSide.SIDE2, right));
  }
}
