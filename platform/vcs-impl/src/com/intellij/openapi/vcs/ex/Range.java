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
package com.intellij.openapi.vcs.ex;

import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class Range {
  private static final Logger LOG = Logger.getInstance(Range.class);
  public static final byte EQUAL = 0;
  public static final byte MODIFIED = 1;
  public static final byte INSERTED = 2;
  public static final byte DELETED = 3;

  // (2,3) - modified 2nd line
  // (2,2) - empty range between 1 and 2 lines
  // index of first line is 0
  private int myLine1;
  private int myLine2;
  private final int myVcsLine1;
  private final int myVcsLine2;

  @Nullable private final List<InnerRange> myInnerRanges;

  @Nullable private RangeHighlighter myRangeHighlighter;
  private boolean myValid = true;

  public Range(@NotNull Range range) {
    this(range.getLine1(), range.getLine2(), range.getVcsLine1(), range.getVcsLine2());
  }

  public Range(int line1, int line2, int vcsLine1, int vcsLine2) {
    this(line1, line2, vcsLine1, vcsLine2, null);
  }

  public Range(int line1, int line2, int vcsLine1, int vcsLine2, @Nullable List<InnerRange> innerRanges) {
    assert line1 != line2 || vcsLine1 != vcsLine2;

    myLine1 = line1;
    myLine2 = line2;
    myVcsLine1 = vcsLine1;
    myVcsLine2 = vcsLine2;
    myInnerRanges = innerRanges;
  }

  public int hashCode() {
    return myVcsLine1 ^ myVcsLine2 ^ myLine1 ^ myLine2;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Range range = (Range)o;

    if (myLine1 != range.myLine1) return false;
    if (myLine2 != range.myLine2) return false;
    if (myVcsLine1 != range.myVcsLine1) return false;
    if (myVcsLine2 != range.myVcsLine2) return false;

    if (myInnerRanges == null) return range.myInnerRanges == null;
    if (range.myInnerRanges == null) return false;

    if (myInnerRanges.size() != range.myInnerRanges.size()) return false;
    for (int i = 0; i < myInnerRanges.size(); i++) {
      if (!myInnerRanges.get(i).equals(range.myInnerRanges.get(i))) return false;
    }

    return true;
  }

  public String toString() {
    return String.format("%s, %s, %s, %s", myLine1, myLine2, myVcsLine1, myVcsLine2);
  }

  public byte getType() {
    if (myLine1 == myLine2) return DELETED;
    if (myVcsLine1 == myVcsLine2) return INSERTED;
    return MODIFIED;
  }

  public void shift(int shift) {
    myLine1 += shift;
    myLine2 += shift;

    if (myInnerRanges != null) {
      for (InnerRange range : myInnerRanges) {
        range.shift(shift);
      }
    }
  }

  @Nullable
  public List<InnerRange> getInnerRanges() {
    return myInnerRanges;
  }

  public int getLine1() {
    return myLine1;
  }

  public int getLine2() {
    return myLine2;
  }

  public int getVcsLine1() {
    return myVcsLine1;
  }

  public int getVcsLine2() {
    return myVcsLine2;
  }

  public void setHighlighter(@Nullable RangeHighlighter highlighter) {
    myRangeHighlighter = highlighter;
  }

  @Nullable
  public RangeHighlighter getHighlighter() {
    return myRangeHighlighter;
  }

  public boolean isValid() {
    return myValid;
  }

  public void invalidate() {
    myValid = false;
  }

  public static class InnerRange {
    private int myLine1;
    private int myLine2;
    private final byte myType;

    public InnerRange(int line1, int line2, byte type) {
      myLine1 = line1;
      myLine2 = line2;
      myType = type;
    }

    public int getLine1() {
      return myLine1;
    }

    public int getLine2() {
      return myLine2;
    }

    public byte getType() {
      return myType;
    }

    public void shift(int shift) {
      myLine1 += shift;
      myLine2 += shift;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      InnerRange range = (InnerRange)o;

      if (myLine1 != range.myLine1) return false;
      if (myLine2 != range.myLine2) return false;
      if (myType != range.myType) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myLine1;
      result = 31 * result + myLine2;
      result = 31 * result + (int)myType;
      return result;
    }

    public String toString() {
      return String.format("%s, %s, %s", myLine1, myLine2, getTypeName(myType));
    }
  }

  /*
   * Check, if caret at <line> is corresponds to the current range
   */
  public boolean isSelectedByLine(int line) {
    return DiffUtil.isSelectedByLine(line, myLine1, myLine2);
  }

  @NotNull
  private static String getTypeName(byte type) {
    switch (type) {
      case MODIFIED:
        return "MODIFIED";
      case INSERTED:
        return "INSERTED";
      case DELETED:
        return "DELETED";
      case EQUAL:
        return "EQUAL";
    }
    return "UNKNOWN(" + type + ")";
  }
}
