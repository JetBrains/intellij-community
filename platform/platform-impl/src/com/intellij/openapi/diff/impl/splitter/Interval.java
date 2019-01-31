// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.splitter;

import java.util.Comparator;

public class Interval {
  public static final Comparator START_COMPARATOR = (o1, o2) -> {
    if (o1 instanceof Interval) return ((Interval) o1).compareToStart((Integer) o2);
    return -((Interval) o2).compareToStart((Integer) o1);
  };

  public static final Comparator END_COMPARATOR = (o1, o2) -> {
    if (o1 instanceof Interval) return ((Interval) o1).compareToEnd((Integer) o2);
    return -((Interval)o2).compareToEnd((Integer) o1);
  };
  private final int myStart;
  private final int myLength;

  public Interval(int start, int length) {
    myStart = start;
    myLength = length;
  }

  public int getEnd() {
    return myStart + myLength;
  }

  public int getStart() {
    return myStart;
  }

  private int compareToStart(Integer start) {
    return myStart - start.intValue();
  }

  private int compareToEnd(Integer end) {
    return getEnd() - end.intValue();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Interval)) return false;
    Interval other = (Interval) obj;
    return myStart == other.myStart && myLength == other.myLength;
  }

  @Override
  public int hashCode() {
    return myStart ^ (myLength << 20);
  }

  @Override
  public String toString() {
    return "[" + myStart + ", " + getEnd() + ")";
  }

  public static Interval fromTo(int start, int end) {
    return new Interval(start, end - start);
  }

  public int getLength() {
    return myLength;
  }

  public static Interval toInf(int start) {
    return Interval.fromTo(start, Integer.MAX_VALUE);
  }

  public boolean contains(int location) {
    return (location > myStart && location < getEnd()) || location == myStart;
  }
}
