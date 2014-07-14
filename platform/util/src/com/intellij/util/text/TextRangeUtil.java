/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.text;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Rustam Vishnyakov
 */
public class TextRangeUtil {

  private final static Comparator<TextRange> RANGE_COMPARATOR = new Comparator<TextRange>() {
    @Override
    public int compare(TextRange range1, TextRange range2) {
      int startOffsetDiff = range1.getStartOffset() - range2.getStartOffset();
      return startOffsetDiff != 0 ? startOffsetDiff : range1.getEndOffset() - range2.getEndOffset();
    }
  };
  
  private TextRangeUtil() {
  }

  /**
   * Excludes ranges from the original range. For example, if the original range is [30..100] and ranges to exclude are
   * [20..50] and [60..90], resulting ranges will be [50..60] and [90..100]. The ranges may overlap and follow in any order. In the latter
   * case the original list of excluded ranges is sorted by start/end offset.
   * 
   * @param original The original range to exclude the ranges from.
   * @param excludedRanges The list of ranges to exclude.
   * @return A list of ranges after excluded ranges have been applied.
   */
  public static Iterable<TextRange> excludeRanges(@NotNull TextRange original, @NotNull List<TextRange> excludedRanges) {
    if (!excludedRanges.isEmpty()) {
      List<TextRange> enabledRanges = new ArrayList<TextRange>();
      if (excludedRanges.size() > 1) {
        Collections.sort(excludedRanges, RANGE_COMPARATOR);
      }
      int enabledRangeStart = original.getStartOffset();
      for (TextRange excludedRange : excludedRanges) {
        if (excludedRange.getEndOffset() < enabledRangeStart) continue;
        int excludedRangeStart = excludedRange.getStartOffset();
        if (excludedRangeStart > original.getEndOffset()) break;
        if (excludedRangeStart > enabledRangeStart) {
          enabledRanges.add(new TextRange(enabledRangeStart, excludedRangeStart));
        }
        enabledRangeStart = excludedRange.getEndOffset();
      }
      if (enabledRangeStart < original.getEndOffset()) {
        enabledRanges.add(new TextRange(enabledRangeStart, original.getEndOffset()));
      }
      return enabledRanges;
    }
    return Arrays.asList(original);
  }
}
