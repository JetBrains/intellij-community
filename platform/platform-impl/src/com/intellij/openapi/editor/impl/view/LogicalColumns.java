// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.ex.DocumentSnapshot;
import com.intellij.util.ArrayUtil;
import com.intellij.util.DocumentInternalUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

final class LogicalColumns {
  private static final int CACHE_FREQUENCY = 1024; // logical column will be cached for each CACHE_FREQUENCY-th character on the line
  private static final LogicalColumns TRIVIAL = new LogicalColumns(null);

  static @NotNull LogicalColumns getTrivial() {
    return TRIVIAL;
  }

  private final int @Nullable [] columnCache;

  private LogicalColumns(int @Nullable [] columnData) {
    columnCache = columnData;
  }

  static LogicalColumns create(@NotNull DocumentSnapshot document, int tabSize, int line) {
    int start = document.lineStartOffset(line);
    int end = document.lineEndOffset(line);
    int cacheSize = (end - start) / CACHE_FREQUENCY;
    int[] cache = ArrayUtil.newIntArray(cacheSize);
    CharSequence text = document.charSequence();
    int column = 0;
    boolean hasTabsOrSurrogates = false;
    for (int i = start; i < end; i++) {
      if (i > start && (i - start) % CACHE_FREQUENCY == 0) {
        cache[(i - start) / CACHE_FREQUENCY - 1] = column;
      }
      char c = text.charAt(i);
      if (c == '\t') {
        column = (column / tabSize + 1) * tabSize;
        hasTabsOrSurrogates = true;
      } else {
        if (Character.isHighSurrogate(c)) {
          hasTabsOrSurrogates = true;
          if (i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
            continue;
          }
        } else {
          hasTabsOrSurrogates |= Character.isLowSurrogate(c);
        }
        column++;
      }
    }
    if (cacheSize > 0 && (end - start) % CACHE_FREQUENCY == 0) {
      cache[cacheSize - 1] = column;
    }
    return hasTabsOrSurrogates ? new LogicalColumns(cache) : TRIVIAL;
  }

  boolean isTrivial() {
    return columnCache == null;
  }

  int offsetToLogicalColumn(@NotNull DocumentSnapshot document, int tabSize, int line, int offset) {
    offset = Math.min(offset, document.lineEndOffset(line));
    int lineStartOffset = document.lineStartOffset(line);
    int relOffset = offset - lineStartOffset;
    if (columnCache == null) {
      return relOffset;
    }
    int cacheIndex = relOffset / CACHE_FREQUENCY;
    int startOffset = lineStartOffset + cacheIndex * CACHE_FREQUENCY;
    int startColumn = cacheIndex == 0 ? 0 : columnCache[cacheIndex - 1];
    return DocumentInternalUtil.calcLogicalColumn(
      document.charSequence(),
      startOffset,
      startColumn,
      offset,
      tabSize
    );
  }

  int logicalColumnToOffset(@NotNull DocumentSnapshot document, int tabSize, int line, int column) {
    int lineStartOffset = document.lineStartOffset(line);
    int lineEndOffset = document.lineEndOffset(line);
    if (columnCache == null) {
      int result = lineStartOffset + column;
      return result < 0 || result > lineEndOffset // guarding over overflow
             ? lineEndOffset
             : result;
    }
    int pos = Arrays.binarySearch(columnCache, column);
    if (pos >= 0) {
      int result = lineStartOffset + (pos + 1) * CACHE_FREQUENCY;
      return DocumentInternalUtil.isInsideSurrogatePair(document, result) ? result - 1 : result;
    }
    int startOffset = lineStartOffset + (- pos - 1) * CACHE_FREQUENCY;
    int cachedColumn = pos == -1 ? 0 : columnCache[- pos - 2];
    return DocumentInternalUtil.calcLogicalOffset(
      document.charSequence(),
      column,
      cachedColumn,
      startOffset,
      lineEndOffset,
      tabSize
    );
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    LogicalColumns data = (LogicalColumns)o;
    return Objects.deepEquals(columnCache, data.columnCache);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(columnCache);
  }
}
