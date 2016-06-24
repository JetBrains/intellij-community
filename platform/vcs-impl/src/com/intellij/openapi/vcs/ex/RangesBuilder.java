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

import com.intellij.diff.comparison.ByLine;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.comparison.TrimUtil;
import com.intellij.diff.comparison.iterables.DiffIterableUtil;
import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.ex.Range.InnerRange;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
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
                                         int currentShift,
                                         int vcsShift,
                                         boolean innerWhitespaceChanges) throws FilesTooBigForDiffException {
    if (innerWhitespaceChanges) {
      return createRangesSmart(current, vcs, currentShift, vcsShift);
    }
    else {
      return createRangesSimple(current, vcs, currentShift, vcsShift);
    }
  }

  @NotNull
  private static List<Range> createRangesSimple(@NotNull List<String> current,
                                                @NotNull List<String> vcs,
                                                int currentShift,
                                                int vcsShift) throws FilesTooBigForDiffException {
    FairDiffIterable iterable = ByLine.compare(vcs, current, ComparisonPolicy.DEFAULT, DumbProgressIndicator.INSTANCE);

    List<Range> result = new ArrayList<Range>();
    for (com.intellij.diff.util.Range range : iterable.iterateChanges()) {
      int vcsLine1 = vcsShift + range.start1;
      int vcsLine2 = vcsShift + range.end1;
      int currentLine1 = currentShift + range.start2;
      int currentLine2 = currentShift + range.end2;

      result.add(new Range(currentLine1, currentLine2, vcsLine1, vcsLine2));
    }
    return result;
  }

  @NotNull
  private static List<Range> createRangesSmart(@NotNull List<String> current,
                                               @NotNull List<String> vcs,
                                               int shift,
                                               int vcsShift) throws FilesTooBigForDiffException {
    FairDiffIterable iwIterable = ByLine.compare(vcs, current, ComparisonPolicy.IGNORE_WHITESPACES, DumbProgressIndicator.INSTANCE);

    RangeBuilder rangeBuilder = new RangeBuilder(current, vcs, shift, vcsShift);

    for (Pair<com.intellij.diff.util.Range, Boolean> pair : DiffIterableUtil.iterateAll(iwIterable)) {
      com.intellij.diff.util.Range range = pair.first;
      Boolean equals = pair.second;

      if (equals) {
        int count = range.end1 - range.start1;
        for (int i = 0; i < count; i++) {
          int vcsIndex = range.start1 + i;
          int currentIndex = range.start2 + i;
          String vcsLine = vcs.get(vcsIndex);
          String currentLine = current.get(currentIndex);

          if (vcsLine.equals(currentLine)) {
            rangeBuilder.flushChange();
          }
          else {
            rangeBuilder.markChangedWhitespaces(vcsIndex, currentIndex);
          }
        }
      }
      else {
        rangeBuilder.markChanged(range.start1, range.end1, range.start2, range.end2);
      }
    }

    return rangeBuilder.finish();
  }

  private static class RangeBuilder {
    @NotNull private final List<String> myCurrent;
    @NotNull private final List<String> myVcs;
    private final int myCurrentShift;
    private final int myVcsShift;

    @NotNull private final List<Range> myRanges = new ArrayList<>();

    private com.intellij.diff.util.Range change;
    private ArrayList<InnerRange> innerRanges;

    public RangeBuilder(@NotNull List<String> current,
                        @NotNull List<String> vcs,
                        int currentShift,
                        int vcsShift) {
      myCurrent = current;
      myVcs = vcs;
      myCurrentShift = currentShift;
      myVcsShift = vcsShift;
    }

    public void flushChange() {
      if (change == null) return;

      for (InnerRange range : innerRanges) {
        range.shift(myCurrentShift);
      }
      innerRanges.trimToSize();

      change = TrimUtil.expand(myVcs, myCurrent, change.start1, change.start2, change.end1, change.end2);

      int currentLine1 = myCurrentShift + change.start2;
      int currentLine2 = myCurrentShift + change.end2;
      int vcsLine1 = myVcsShift + change.start1;
      int vcsLine2 = myVcsShift + change.end1;
      myRanges.add(new Range(currentLine1, currentLine2, vcsLine1, vcsLine2, innerRanges));

      change = null;
      innerRanges = null;
    }

    public void markChangedWhitespaces(int vcsIndex, int currentIndex) {
      appendChangedLine(vcsIndex, vcsIndex + 1, currentIndex, currentIndex + 1);
      appendInnerEquals(vcsIndex, vcsIndex + 1, currentIndex, currentIndex + 1);
    }

    public void markChanged(int vcsStart, int vcsEnd, int currentStart, int currentEnd) {
      appendChangedLine(vcsStart, vcsEnd, currentStart, currentEnd);
      appendInnerChange(vcsStart, vcsEnd, currentStart, currentEnd);
    }

    @NotNull
    public List<Range> finish() {
      flushChange();
      return myRanges;
    }

    private void appendChangedLine(int vcsStart, int vcsEnd, int currentStart, int currentEnd) {
      if (change == null) {
        change = new com.intellij.diff.util.Range(vcsStart, vcsEnd, currentStart, currentEnd);
        innerRanges = new ArrayList<>();
      }
      else {
        assert vcsStart == change.end1;
        assert currentStart == change.end2;
        change = new com.intellij.diff.util.Range(change.start1, vcsEnd, change.start2, currentEnd);
      }
    }

    private void appendInnerChange(int vcsStart, int vcsEnd, int currentStart, int currentEnd) {
      byte type = getChangeType(vcsStart, vcsEnd, currentStart, currentEnd);
      innerRanges.add(new InnerRange(currentStart, currentEnd, type));
    }

    private void appendInnerEquals(int vcsStart, int vcsEnd, int currentStart, int currentEnd) {
      InnerRange last = ContainerUtil.getLastItem(innerRanges);
      if (last == null || last.getType() != Range.EQUAL) {
        innerRanges.add(new InnerRange(currentStart, currentEnd, Range.EQUAL));
      }
      else {
        assert currentStart == last.getLine2();
        innerRanges.set(innerRanges.size() - 1, new InnerRange(last.getLine1(), currentEnd, Range.EQUAL));
      }
    }
  }

  private static byte getChangeType(int vcsStart, int vcsEnd, int currentStart, int currentEnd) {
    int deleted = vcsEnd - vcsStart;
    int inserted = currentEnd - currentStart;
    if (deleted > 0 && inserted > 0) return Range.MODIFIED;
    if (deleted > 0) return Range.DELETED;
    if (inserted > 0) return Range.INSERTED;
    LOG.error("Unknown change type");
    return Range.EQUAL;
  }
}
