// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Collections.emptySet;

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
   *
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
    if (!s.contains(prefix)) {
      return emptySet();
    }

    Set<TextRange> ranges = new LinkedHashSet<>(2);
    Deque<Integer> prefixes = new ArrayDeque<>();

    int searchFrom = 0;
    while (searchFrom < s.length()) {
      int nextSuffix = s.indexOf(suffix, searchFrom);
      if (nextSuffix < 0) {
        break; // no more suffixes to create ranges
      }
      int nextPrefix = s.indexOf(prefix, searchFrom);

      if (prefixes.isEmpty()) {
        if (nextPrefix < 0) {
          break; // no more prefixes to match with suffix
        }
        prefixes.push(nextPrefix);
        searchFrom = nextPrefix + 1;
      }
      else if (nextPrefix < 0
               || nextSuffix <= nextPrefix
               || nextPrefix + prefix.length() > nextSuffix) { // support overlapping tokens
        // suffix first
        int prefixPairPos = prefixes.pop();

        int startOffset = prefixPairPos + (useFullTextRange ? 0 : prefix.length());
        int endOffset = useFullTextRange ? nextSuffix + suffix.length() : nextSuffix;

        ranges.add(TextRange.create(startOffset, endOffset));

        // remove overlapping prefix positions from stack
        while (!prefixes.isEmpty() && prefixes.peek() + prefix.length() > prefixPairPos) {
          prefixes.pop();
        }

        searchFrom = nextSuffix + suffix.length();
      }
      else {
        // prefix first
        prefixes.push(nextPrefix);
        searchFrom = nextPrefix + 1;
      }
    }

    return filterNestedRanges ? filterNested(ranges) : ranges;
  }

  private static Set<TextRange> filterNested(Set<? extends TextRange> allRanges) {
    Set<TextRange> filtered = new LinkedHashSet<>(allRanges.size());
    for (TextRange outer : allRanges) {
      boolean contains = anyRangeContains(allRanges, outer);
      if (!contains) filtered.add(outer);
    }

    return filtered;
  }

  private static boolean anyRangeContains(Set<? extends TextRange> allRanges, TextRange inner) {
    for (TextRange outer : allRanges) {
      if (!inner.equals(outer) &&
          outer.contains(inner)) {
        return true;
      }
    }
    return false;
  }
}
