// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class PlaceholderTextRanges {

  private PlaceholderTextRanges() {
  }

  /**
   * @see #getPlaceholderRanges(String, String, String, boolean, boolean)
   */
  @NotNull
  public static Set<TextRange> getPlaceholderRanges(@NotNull String s, @NotNull String prefix, @NotNull String suffix) {
    return getPlaceholderRanges(s, prefix, suffix, false);
  }

  /**
   * @see #getPlaceholderRanges(String, String, String, boolean, boolean)
   */
  @NotNull
  public static Set<TextRange> getPlaceholderRanges(@NotNull String s,
                                                    @NotNull String prefix,
                                                    @NotNull String suffix,
                                                    boolean useFullTextRange) {
    return getPlaceholderRanges(s, prefix, suffix, useFullTextRange, false);
  }

  /**
   * Searches for placeholder text ranges with the given prefix and suffix.
   * @param s                  String to parse.
   * @param prefix             Prefix.
   * @param suffix             Suffix.
   * @param useFullTextRange   Use full text range (incl. prefix/suffix).
   * @param filterNestedRanges Remove nested ranges from result.
   * @return Matching ranges.
   */
  @NotNull
  public static Set<TextRange> getPlaceholderRanges(@NotNull String s,
                                                    @NotNull String prefix,
                                                    @NotNull String suffix,
                                                    boolean useFullTextRange,
                                                    boolean filterNestedRanges) {
    int current = s.indexOf(prefix);
    if (current == -1) {
      return Collections.emptySet();
    }

    Set<TextRange> ranges = new LinkedHashSet<>(2);

    Deque<Integer> prefixes = new ArrayDeque<>();
    prefixes.push(current);
    boolean currentPointsAtPrefix = true;

    while (current >= 0) {
      int nextSuffix = s.indexOf(suffix, currentPointsAtPrefix ? current + prefix.length() : current);
      if (nextSuffix == -1) {
        break;
      }

      int nextPrefix = s.indexOf(prefix, current + 1);

      while (nextPrefix > 0 && nextPrefix + prefix.length() <= nextSuffix) {
        prefixes.push(nextPrefix);
        nextPrefix = s.indexOf(prefix, nextPrefix + 1);
      }

      nextPrefix = prefixes.pop();
      int startOffset = nextPrefix + (useFullTextRange ? 0 : prefix.length());
      int endOffset = useFullTextRange ? nextSuffix + suffix.length() : nextSuffix;

      TextRange textRange = new TextRange(startOffset, endOffset);
      ranges.add(textRange);

      while (!prefixes.isEmpty() && prefixes.peek() + prefix.length() > nextPrefix) {
        prefixes.pop();
      }

      current = s.indexOf(prefix, nextSuffix + suffix.length());
      if (current > 0) {
        prefixes.push(current);
        currentPointsAtPrefix = true;
      }
      else if (!prefixes.isEmpty()) {
        current = s.indexOf(suffix, nextSuffix + suffix.length());
        currentPointsAtPrefix = false;
      }
    }

    return filterNestedRanges ? filterNested(ranges) : ranges;
  }

  private static Set<TextRange> filterNested(Set<TextRange> allRanges) {
    Set<TextRange> filtered = new LinkedHashSet<>(allRanges.size());
    for (TextRange outer : allRanges) {
      boolean contains = anyRangeContains(allRanges, outer);
      if (!contains) filtered.add(outer);
    }

    return filtered;
  }

  private static boolean anyRangeContains(Set<TextRange> allRanges, TextRange inner) {
    for (TextRange outer : allRanges) {
      if (!inner.equals(outer) &&
          outer.contains(inner)) {
        return true;
      }
    }
    return false;
  }
}
