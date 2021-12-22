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
package com.intellij.diff;

import com.intellij.diff.comparison.ByLine;
import com.intellij.diff.comparison.CancellationChecker;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Range;
import com.intellij.openapi.diagnostic.Logger;
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

  @NotNull
  public Block createPreviousBlock(@NotNull String prevContent) {
    return createPreviousBlock(tokenize(prevContent));
  }

  @NotNull
  public Block createPreviousBlock(String @NotNull [] prevContent) {
    try {
      FairDiffIterable iterable = ByLine.compare(Arrays.asList(prevContent), Arrays.asList(mySource),
                                                 ComparisonPolicy.IGNORE_WHITESPACES, CancellationChecker.EMPTY);

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

  @NotNull
  public String getBlockContent() {
    return StringUtil.join(getLines(), "\n");
  }

  @NotNull
  public List<String> getLines() {
    return Arrays.asList(mySource).subList(myStart, myEnd);
  }

  public int hashCode() {
    return Arrays.hashCode(mySource) ^ myStart ^ myEnd;
  }

  public boolean equals(Object object) {
    if (!(object instanceof Block)) return false;
    Block other = (Block)object;
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
