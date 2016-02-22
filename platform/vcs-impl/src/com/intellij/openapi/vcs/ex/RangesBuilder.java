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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RangesBuilder {
  private static final Logger LOG = Logger.getInstance(RangesBuilder.class);

  @NotNull
  public static List<Range> createRanges(@NotNull Document current, @NotNull Document vcs) throws FilesTooBigForDiffException {
    return createRanges(current, vcs, false);
  }

  @NotNull
  public static List<Range> createRanges(@NotNull Document current, @NotNull Document vcs, boolean innerWhitespaceChanges)
    throws FilesTooBigForDiffException {
    return createRanges(DiffUtil.getLines(current), DiffUtil.getLines(vcs), 0, 0, innerWhitespaceChanges);
  }

  @NotNull
  public static List<Range> createRanges(@NotNull List<String> current,
                                         @NotNull List<String> vcs,
                                         int shift,
                                         int vcsShift,
                                         boolean innerWhitespaceChanges) throws FilesTooBigForDiffException {
    Diff.Change ch = Diff.buildChanges(ArrayUtil.toStringArray(vcs), ArrayUtil.toStringArray(current));

    List<Range> result = new ArrayList<Range>();
    while (ch != null) {
      if (innerWhitespaceChanges) {
        result.add(createOnSmart(ch, shift, vcsShift, current, vcs));
      }
      else {
        result.add(createOn(ch, shift, vcsShift));
      }
      ch = ch.link;
    }
    return result;
  }

  private static Range createOn(@NotNull Diff.Change change, int shift, int vcsShift) {
    int offset1 = shift + change.line1;
    int offset2 = offset1 + change.inserted;

    int uOffset1 = vcsShift + change.line0;
    int uOffset2 = uOffset1 + change.deleted;

    return new Range(offset1, offset2, uOffset1, uOffset2);
  }

  private static Range createOnSmart(@NotNull Diff.Change change,
                                     int shift,
                                     int vcsShift,
                                     @NotNull List<String> current,
                                     @NotNull List<String> vcs) throws FilesTooBigForDiffException {
    byte type = getChangeType(change);

    int offset1 = shift + change.line1;
    int offset2 = offset1 + change.inserted;

    int uOffset1 = vcsShift + change.line0;
    int uOffset2 = uOffset1 + change.deleted;

    if (type != Range.MODIFIED) {
      return new Range(offset1, offset2, uOffset1, uOffset2, Collections.singletonList(new Range.InnerRange(offset1, offset2, type)));
    }

    LineWrapper[] lines1 = new LineWrapper[change.deleted];
    LineWrapper[] lines2 = new LineWrapper[change.inserted];
    for (int i = 0; i < change.deleted; i++) {
      lines1[i] = new LineWrapper(vcs.get(i + change.line0));
    }
    for (int i = 0; i < change.inserted; i++) {
      lines2[i] = new LineWrapper(current.get(i + change.line1));
    }

    Diff.Change ch = Diff.buildChanges(lines1, lines2);

    List<Range.InnerRange> inner = new ArrayList<Range.InnerRange>();

    int last0 = 0;
    int last1 = 0;
    while (ch != null) {
      if (ch.line0 != last0 && ch.line1 != last1) {
        byte innerType = Range.EQUAL;
        int innerStart = shift + change.line1 + last1;
        int innerEnd = shift + change.line1 + ch.line1;
        inner.add(new Range.InnerRange(innerStart, innerEnd, innerType));
      }

      byte innerType = getChangeType(ch);
      int innerStart = shift + change.line1 + ch.line1;
      int innerEnd = innerStart + ch.inserted;
      inner.add(new Range.InnerRange(innerStart, innerEnd, innerType));

      last0 = ch.line0 + ch.deleted;
      last1 = ch.line1 + ch.inserted;

      ch = ch.link;
    }
    if (change.deleted != last0 && change.inserted != last1) {
      byte innerType = Range.EQUAL;
      int innerStart = shift + change.line1 + last1;
      int innerEnd = shift + change.line1 + change.inserted;
      inner.add(new Range.InnerRange(innerStart, innerEnd, innerType));
    }

    return new Range(offset1, offset2, uOffset1, uOffset2, inner);
  }

  private static byte getChangeType(@NotNull Diff.Change change) {
    if ((change.deleted > 0) && (change.inserted > 0)) return Range.MODIFIED;
    if ((change.deleted > 0)) return Range.DELETED;
    if ((change.inserted > 0)) return Range.INSERTED;
    LOG.error("Unknown change type");
    return Range.EQUAL;
  }

  private static class LineWrapper {
    @NotNull private final String myLine;
    private final int myHash;

    public LineWrapper(@NotNull String line) {
      myLine = line;
      myHash = StringUtil.stringHashCodeIgnoreWhitespaces(line);
    }

    @NotNull
    public String getLine() {
      return myLine;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      LineWrapper wrapper = (LineWrapper)o;

      if (myHash != wrapper.myHash) return false;

      return StringUtil.equalsIgnoreWhitespaces(myLine, wrapper.myLine);
    }

    @Override
    public int hashCode() {
      return myHash;
    }
  }
}
