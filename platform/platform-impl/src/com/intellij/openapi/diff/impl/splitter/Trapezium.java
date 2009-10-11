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

  public boolean equals(Object obj) {
    if (!(obj instanceof Trapezium)) return false;
    Trapezium other = (Trapezium) obj;
    return myBase1.equals(other.myBase1) && myBase2.equals(other.myBase2);
  }

  public int hashCode() {
    return myBase1.hashCode() ^ myBase2.hashCode();
  }

  public String toString() {
    return "{" + myBase1 + ", " + myBase2 + "}";
  }
}
