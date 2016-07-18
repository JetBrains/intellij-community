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

    List<Range> result = new ArrayList<>();
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
            rangeBuilder.markChanged(vcsIndex, currentIndex);
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

    @NotNull private final List<Range> myResult = new ArrayList<>();

    private int vcsLine1 = -1;
    private int vcsLine2 = -1;
    private int currentLine1 = -1;
    private int currentLine2 = -1;

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
      if (vcsLine1 == -1) return;

      int forward = TrimUtil.expandForward(myVcs, myCurrent, vcsLine1, currentLine1, vcsLine2, currentLine2);
      vcsLine1 += forward;
      currentLine1 += forward;

      int backward = TrimUtil.expandBackward(myVcs, myCurrent, vcsLine1, currentLine1, vcsLine2, currentLine2);
      vcsLine2 -= backward;
      currentLine2 -= backward;

      List<String> vcs = myVcs.subList(vcsLine1, vcsLine2);
      List<String> current = myCurrent.subList(currentLine1, currentLine2);
      List<InnerRange> inner = calcInnerRanges(vcs, current, myCurrentShift + currentLine1);

      myResult.add(new Range(myCurrentShift + currentLine1, myCurrentShift + currentLine2,
                             myVcsShift + vcsLine1, myVcsShift + vcsLine2,
                             inner));


      currentLine1 = -1;
      currentLine2 = -1;
      vcsLine1 = -1;
      vcsLine2 = -1;
    }

    public void markChanged(int vcsIndex, int currentIndex) {
      markChanged(vcsIndex, vcsIndex + 1, currentIndex, currentIndex + 1);
    }

    public void markChanged(int vcsStart, int vcsEnd, int currentStart, int currentEnd) {
      if (vcsLine1 == -1) {
        vcsLine1 = vcsStart;
        vcsLine2 = vcsEnd;
        currentLine1 = currentStart;
        currentLine2 = currentEnd;
      }
      else {
        assert vcsStart == vcsLine2;
        assert currentStart == currentLine2;
        vcsLine2 = vcsEnd;
        currentLine2 = currentEnd;
      }
    }

    @NotNull
    public List<Range> finish() {
      flushChange();
      return myResult;
    }
  }

  @NotNull
  private static List<InnerRange> calcInnerRanges(@NotNull List<String> vcs, @NotNull List<String> current, int startOffset) {
    ArrayList<InnerRange> result = new ArrayList<>();
    FairDiffIterable iwIterable = ByLine.compare(vcs, current, ComparisonPolicy.IGNORE_WHITESPACES, DumbProgressIndicator.INSTANCE);
    for (Pair<com.intellij.diff.util.Range, Boolean> pair : DiffIterableUtil.iterateAll(iwIterable)) {
      com.intellij.diff.util.Range range = pair.first;
      Boolean equals = pair.second;

      byte type = equals ? Range.EQUAL : getChangeType(range.start1, range.end1, range.start2, range.end2);
      result.add(new InnerRange(range.start2 + startOffset, range.end2 + startOffset, type));
    }
    result.trimToSize();
    return result;
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
