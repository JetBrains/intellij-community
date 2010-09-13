/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
   * Maps given offset to the index of target {@link CacheEntry} that holds information about visual line with that offset.
   *
   * @param offset    target offset
   * @param document  document which dimensions are being mapped
   * @param cache     parsed cache entries list that hold information about given document positions and are sorted by visual lines
   *                  in ascending order
   * @return          index of target {@link CacheEntry} that holds information about visual line with given offset if possible;
   *                  <code>-1</code> if it's not possible to perform such mapping
   */
  public static int getCacheEntryIndexForOffset(int offset, Document document, List<CacheEntry> cache) {
    if (offset >= document.getTextLength()) {
      if (cache.isEmpty()) {
        return -1;
      }
      else {
        return cache.size() - 1;
      }
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

    return -1;
  }

}
