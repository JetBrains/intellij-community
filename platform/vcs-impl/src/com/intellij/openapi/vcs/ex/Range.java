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

  private int myOffset1;
  private int myOffset2;
  private final int myUpToDateOffset1;
  private final int myUpToDateOffset2;
  private final byte myType;
  @Nullable private RangeHighlighter myRangeHighlighter;

  public static Range createOn(@NotNull Diff.Change change, int shift, int upToDateShift) {
    byte type = getChangeTypeFrom(change);

    int offset1 = shift + change.line1;
    int offset2 = offset1 + change.inserted;

    int uOffset1 = upToDateShift + change.line0;
    int uOffset2 = uOffset1 + change.deleted;

    return new Range(offset1, offset2, uOffset1, uOffset2, type);
  }

  private static byte getChangeTypeFrom(@NotNull Diff.Change change) {
    if ((change.deleted > 0) && (change.inserted > 0)) return MODIFIED;
    if ((change.deleted > 0)) return DELETED;
    if ((change.inserted > 0)) return INSERTED;
    LOG.error("Unknown change type");
    return 0;
  }

  public Range(int offset1, int offset2, int uOffset1, int uOffset2, byte type) {
    myOffset1 = offset1;
    myOffset2 = offset2;
    myUpToDateOffset1 = uOffset1;
    myUpToDateOffset2 = uOffset2;
    myType = type;
  }

  public int hashCode() {
    return myUpToDateOffset1 ^ myUpToDateOffset2 ^ myType ^ myOffset1 ^ myOffset2;
  }

  public boolean equals(Object object) {
    if (!(object instanceof Range)) return false;
    Range other = (Range)object;
    return
      (myUpToDateOffset1 == other.myUpToDateOffset1)
      && (myUpToDateOffset2 == other.myUpToDateOffset2)
      && (myOffset1 == other.myOffset1)
      && (myOffset2 == other.myOffset2)
      && (myType == other.myType);
  }

  public String toString() {
    return String.format("%s, %s, %s, %s, %s", myOffset1, myOffset2, myUpToDateOffset1, myUpToDateOffset2, getTypeName());
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
    return myUpToDateOffset2 - myUpToDateOffset1;
  }

  public void shift(int shift) {
    myOffset1 += shift;
    myOffset2 += shift;
  }

  public int getOffset1() {
    return myOffset1;
  }

  public int getOffset2() {
    return myOffset2;
  }

  public int getUOffset1() {
    return myUpToDateOffset1;
  }

  public int getUOffset2() {
    return myUpToDateOffset2;
  }

  public boolean canBeMergedWith(@NotNull Range range) {
    return myOffset2 == range.myOffset1;
  }

  @NotNull
  public Range mergeWith(@NotNull Range range) {
    return new Range(myOffset1, range.myOffset2, myUpToDateOffset1, range.myUpToDateOffset2, mergedStatusWith(range));
  }

  private byte mergedStatusWith(@NotNull Range range) {
    byte type = myType;
    if (myType != range.myType) type = MODIFIED;
    return type;
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

  public boolean contains(int offset1, int offset2) {
    return  getOffset1() <= offset1 && getOffset2() >= offset2;
  }

  public boolean containsLine(int line) {
    if (myType == DELETED) return (myOffset1 - 1) <= line
                                  && (myOffset2) >= line;
    return myOffset1 <= line && myOffset2 >= line;
  }

  public boolean isAfter(int line) {
    if (myType == DELETED)
      return (getOffset1() - 1) > line;
    else
      return getOffset1() > line;
  }

}
