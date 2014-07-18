package com.intellij.openapi.vcs.ex;

import org.jetbrains.annotations.Nullable;

public class SegmentTree {
  private final int myActualLength;
  private final int myLength;

  private final Node myRoot;

  public SegmentTree(int length) {
    myActualLength = length;
    myLength = toUpperSquare(length);
    myRoot = new Node();
  }

  public void mark(int pos) {
    mark(pos, pos + 1);
  }

  public void mark(int start, int end) {
    start = correct(0, myActualLength, start);
    end = correct(0, myActualLength, end);

    myRoot.mark(0, myLength, start, end);
  }

  public boolean check(int pos) {
    return check(pos, pos + 1);
  }

  public boolean check(int start, int end) {
    start = correct(0, myActualLength, start);
    end = correct(0, myActualLength, end);

    return myRoot.check(0, myLength, start, end);
  }

  private static int toUpperSquare(int value) {
    int high = Integer.highestOneBit(value);
    return high == value ? value : high * 2;
  }

  private static class Node {
    @Nullable
    public Node myLeft;

    @Nullable
    public Node myRight;

    public boolean myMarked;

    public boolean mark(int thisStart, int thisEnd, int start, int end) {
      if (myLeft == null && myMarked) return true;

      if (start == end) return false;

      myMarked = true;

      if (thisStart == start && thisEnd == end) {
        myLeft = null;
        myRight = null;
        return true;
      }

      if (myLeft == null) {
        myLeft = new Node();
        myRight = new Node();
      }

      int mid = thisStart + (thisEnd - thisStart) / 2;
      int start1 = correct(thisStart, mid, start);
      int end1 = correct(thisStart, mid, end);
      int start2 = correct(mid, thisEnd, start);
      int end2 = correct(mid, thisEnd, end);

      boolean marked = true;
      marked &= myLeft.mark(thisStart, mid, start1, end1);
      marked &= myRight.mark(mid, thisEnd, start2, end2);

      if (marked) {
        myLeft = null;
        myRight = null;
      }

      return marked;
    }

    public boolean check(int thisStart, int thisEnd, int start, int end) {
      if (start == end) return false;

      if (thisStart == start && thisEnd == end) {
        return myMarked;
      }

      if (myLeft == null) return myMarked;

      int mid = thisStart + (thisEnd - thisStart) / 2;
      int start1 = correct(thisStart, mid, start);
      int end1 = correct(thisStart, mid, end);
      int start2 = correct(mid, thisEnd, start);
      int end2 = correct(mid, thisEnd, end);

      if (myLeft.check(thisStart, mid, start1, end1)) return true;
      if (myRight.check(mid, thisEnd, start2, end2)) return true;

      return false;
    }
  }

  private static int correct(int start, int end, int value) {
    if (value < start) return start;
    if (value > end) return end;
    return value;
  }
}
