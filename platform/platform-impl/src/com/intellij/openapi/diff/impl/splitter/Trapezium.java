// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.splitter;

import com.intellij.openapi.diff.impl.highlighting.FragmentSide;

class Trapezium {
  private final Interval myBase1;
  private final Interval myBase2;

  public Trapezium(int start1, int length1, int start2, int length2) {
    this(new Interval(start1, length1), new Interval(start2, length2));
  }

  public Trapezium(Interval base1, Interval base2) {
    myBase1 = base1;
    myBase2 = base2;
  }

  public Interval getBase1() { return myBase1; }

  public Interval getBase2() { return myBase2; }

  public Interval getBase(FragmentSide side) {
    if (FragmentSide.SIDE1 == side) return getBase1();
    if (FragmentSide.SIDE2 == side) return getBase2();
    throw side.invalidException();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Trapezium)) return false;
    Trapezium other = (Trapezium) obj;
    return myBase1.equals(other.myBase1) && myBase2.equals(other.myBase2);
  }

  @Override
  public int hashCode() {
    return myBase1.hashCode() ^ myBase2.hashCode();
  }

  @Override
  public String toString() {
    return "{" + myBase1 + ", " + myBase2 + "}";
  }
}
