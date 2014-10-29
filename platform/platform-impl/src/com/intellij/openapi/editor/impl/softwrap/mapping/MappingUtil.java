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
package com.intellij.openapi.editor.impl.softwrap.mapping;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.LogicalPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Holds utility methods for document dimensions mapping.
 *
 * @author Denis Zhdanov
 * @since Sep 9, 2010 9:38:42 AM
 */
public class MappingUtil {

  private MappingUtil() {
  }

  /**
   * Tries to map given offset to the index of target {@link CacheEntry} that holds information about visual line with that offset.
   *
   * @param offset    target offset
   * @param document  document which dimensions are being mapped
   * @param cache     parsed cache entries list that hold information about given document positions and are sorted by visual lines
   *                  in ascending order
   * @return          non-negative index of target {@link CacheEntry} that holds information about visual line with given offset
   *                  if possible; negative value that indicates insertion point of cache entry for the given offset is to be located.
   *                  That value follows {@link Collections#binarySearch(List, Object)} contract, i.e. real index is calculated
   *                  by <code>'-returned_index - 1'</code>
   */
  public static int getCacheEntryIndexForOffset(int offset, Document document, List<CacheEntry> cache) {
    if (offset >= document.getTextLength() && (cache.isEmpty() || cache.get(cache.size() - 1).endOffset < offset)) {
      return -(cache.size() + 1);
    }

    int start = 0;
    int end = cache.size() - 1;

    // We inline binary search here because profiling indicates that it becomes bottleneck to use Collections.binarySearch().
    while (start <= end) {
      int i = (end + start) >>> 1;
      CacheEntry cacheEntry = cache.get(i);
      if (cacheEntry.endOffset < offset) {
        start = i + 1;
        continue;
      }
      if (cacheEntry.startOffset > offset) {
        end = i - 1;
        continue;
      }

      // There is a possible case that currently found cache entry corresponds to soft-wrapped line and soft wrap occurred
      // at target offset. We need to return cache entry for the next visual line then (because document offset shared with
      // soft wrap offset is assumed to point to 'after soft wrap' position).
      if (offset == cacheEntry.endOffset && i < cache.size() - 1) {
        CacheEntry nextLineCacheEntry = cache.get(i + 1);
        if (nextLineCacheEntry.startOffset == offset) {
          return i + 1;
        }
      }
      return i;
    }

    return -(start + 1);
  }

  @Nullable
  public static CacheEntry getCacheEntryForLogicalPosition(@NotNull LogicalPosition position, @NotNull List<CacheEntry> cache) {
    int start = 0;
    int end = cache.size() - 1;

    while (start <= end) {
      int i = (end + start) >>> 1;
      CacheEntry cacheEntry = cache.get(i);
      if (cacheEntry.startLogicalLine < position.line
          || cacheEntry.startLogicalLine == position.line && cacheEntry.startLogicalColumn < position.column) {
        start = i + 1;
        continue;
      }
      if (cacheEntry.startLogicalLine > position.line
        || cacheEntry.startLogicalLine == position.line && cacheEntry.startLogicalColumn > position.column) {
        end = i - 1;
        continue;
      }

      return assertEnd(position, cache.get(i));
    }
    return end < 0 ? null : assertEnd(position, cache.get(end));
  }

  @Nullable
  private static CacheEntry assertEnd(@NotNull LogicalPosition position, @NotNull CacheEntry entry) {
    return position.line <= entry.endLogicalLine ? entry : null;
  }
}
