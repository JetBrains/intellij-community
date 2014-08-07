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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.util.diff.Diff;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * author: lesya
 */
public class Range {
  private static final Logger LOG = Logger.getInstance(Range.class);
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
  private final byte myType;
  @Nullable private RangeHighlighter myRangeHighlighter;

  private boolean myValid = true;

  public static Range createOn(@NotNull Diff.Change change, int shift, int vcsShift) {
    byte type = getChangeTypeFrom(change);

    int line1 = shift + change.line1;
    int line2 = line1 + change.inserted;

    int vcsLine1 = vcsShift + change.line0;
    int vcsLine2 = vcsLine1 + change.deleted;

    return new Range(line1, line2, vcsLine1, vcsLine2, type);
  }

  private static byte getChangeTypeFrom(@NotNull Diff.Change change) {
    if ((change.deleted > 0) && (change.inserted > 0)) return MODIFIED;
    if ((change.deleted > 0)) return DELETED;
    if ((change.inserted > 0)) return INSERTED;
    LOG.error("Unknown change type");
    return 0;
  }

  public Range(@NotNull Range range) {
    this(range.getLine1(), range.getLine2(), range.getVcsLine1(), range.getVcsLine2(), range.getType());
  }

  public Range(int line1, int line2, int vcsLine1, int vcsLine2, byte type) {
    myLine1 = line1;
    myLine2 = line2;
    myVcsLine1 = vcsLine1;
    myVcsLine2 = vcsLine2;
    myType = type;
  }

  public int hashCode() {
    return myVcsLine1 ^ myVcsLine2 ^ myType ^ myLine1 ^ myLine2;
  }

  public boolean equals(Object object) {
    if (!(object instanceof Range)) return false;
    Range other = (Range)object;
    return
      (myVcsLine1 == other.myVcsLine1)
      && (myVcsLine2 == other.myVcsLine2)
      && (myLine1 == other.myLine1)
      && (myLine2 == other.myLine2)
      && (myType == other.myType);
  }

  public String toString() {
    return String.format("%s, %s, %s, %s, %s", myLine1, myLine2, myVcsLine1, myVcsLine2, getTypeName());
  }

  @NonNls
  private String getTypeName() {
    switch (myType) {
      case MODIFIED:
        return "MODIFIED";
      case INSERTED:
        return "INSERTED";
      case DELETED:
        return "DELETED";
    }
    return "UNKNOWN";
  }

  public byte getType() {
    return myType;
  }

  public int getUpToDateRangeLength() {
    return myVcsLine2 - myVcsLine1;
  }

  public void shift(int shift) {
    myLine1 += shift;
    myLine2 += shift;
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

  public boolean rightBefore(@NotNull Range range) {
    return myLine2 == range.myLine1;
  }

  public boolean hasHighlighter() {
    return myRangeHighlighter != null;
  }

  public void setHighlighter(RangeHighlighter highlighter) {
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
}
