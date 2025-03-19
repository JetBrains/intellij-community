// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff;

import com.intellij.diff.comparison.ByLine;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Range;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * author: lesya
 */
public class Block {
  private static final Logger LOG = Logger.getInstance(Block.class);

  private final String @NotNull [] mySource;
  private final int myStart;
  private final int myEnd;

  public Block(@NotNull String source, int start, int end) {
    this(tokenize(source), start, end);
  }

  public Block(String @NotNull [] source, int start, int end) {
    mySource = source;
    myStart = DiffUtil.bound(start, 0, source.length);
    myEnd = DiffUtil.bound(end, myStart, source.length);
  }

  public static String @NotNull [] tokenize(@NotNull String text) {
    return LineTokenizer.tokenize(text, false, false);
  }

  public @NotNull Block createPreviousBlock(@NotNull String prevContent) {
    return createPreviousBlock(tokenize(prevContent));
  }

  public @NotNull Block createPreviousBlock(String @NotNull [] prevContent) {
    try {
      FairDiffIterable iterable = ByLine.compare(Arrays.asList(prevContent), Arrays.asList(mySource),
                                                 ComparisonPolicy.IGNORE_WHITESPACES, DumbProgressIndicator.INSTANCE);

      // empty range should not be transferred to the non-empty range
      boolean greedy = myStart != myEnd;

      int start = myStart;
      int end = myEnd;
      int shift = 0;

      for (Range range : iterable.iterateChanges()) {
        int changeStart = range.start2 + shift;
        int changeEnd = range.end2 + shift;
        int changeShift = (range.end1 - range.start1) - (range.end2 - range.start2);

        DiffUtil.UpdatedLineRange updatedRange =
          DiffUtil.updateRangeOnModification(start, end, changeStart, changeEnd, changeShift, greedy);

        start = updatedRange.startLine;
        end = updatedRange.endLine;
        shift += changeShift;
      }

      if (start < 0 || end > prevContent.length || end < start) {
        LOG.error("Invalid block range: [" + start + ", " + end + "); length - " + prevContent.length);
      }


      // intern strings, reducing memory usage
      for (Range range : iterable.iterateUnchanged()) {
        int count = range.end1 - range.start1;
        for (int i = 0; i < count; i++) {
          int prevIndex = range.start1 + i;
          int sourceIndex = range.start2 + i;
          if (prevContent[prevIndex].equals(mySource[sourceIndex])) {
            prevContent[prevIndex] = mySource[sourceIndex];
          }
        }
      }

      return new Block(prevContent, start, end);
    }
    catch (DiffTooBigException e) {
      return new Block(prevContent, 0, 0);
    }
  }

  public @NotNull String getBlockContent() {
    return StringUtil.join(getLines(), "\n");
  }

  public @NotNull List<String> getLines() {
    return Arrays.asList(mySource).subList(myStart, myEnd);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(mySource) ^ myStart ^ myEnd;
  }

  @Override
  public boolean equals(Object object) {
    if (!(object instanceof Block other)) return false;
    return myStart == other.myStart
           && myEnd == other.myEnd
           && Arrays.equals(mySource, other.mySource);
  }

  public int getStart() {
    return myStart;
  }

  public int getEnd() {
    return myEnd;
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();

    appendLines(result, 0, myStart);

    result.append("<-----------------------------\n");

    appendLines(result, myStart, myEnd);

    result.append("----------------------------->\n");

    appendLines(result, myEnd, mySource.length);

    return result.toString();
  }

  private void appendLines(@NotNull StringBuilder result, int from, int to) {
    for (int i = from; i < to; i++) {
      result.append(mySource[i]);
      result.append("\n");
    }
  }
}
