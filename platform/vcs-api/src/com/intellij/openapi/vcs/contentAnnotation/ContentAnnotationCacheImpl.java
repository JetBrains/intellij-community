// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.contentAnnotation;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.HistoryCacheWithRevisionKey;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

import java.util.Date;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

final class ContentAnnotationCacheImpl implements ContentAnnotationCache {
  private final Cache<HistoryCacheWithRevisionKey, TreeMap<Integer, Long>> cache;

  ContentAnnotationCacheImpl() {
    cache = Caffeine.newBuilder().maximumSize(50).build();
  }

  @Override
  public @NotNull ThreeState isRecent(VirtualFile vf,
                                      VcsKey vcsKey,
                                      VcsRevisionNumber number,
                                      TextRange range, long boundTime) {
    TreeMap<Integer, Long> treeMap =
      cache.getIfPresent(new HistoryCacheWithRevisionKey(VcsContextFactory.getInstance().createFilePathOn(vf), vcsKey, number));
    if (treeMap != null) {
      Map.Entry<Integer, Long> last = treeMap.floorEntry(range.getEndOffset());
      if (last == null || last.getKey() < range.getStartOffset()) {
        return ThreeState.NO;
      }

      Map.Entry<Integer, Long> first = treeMap.ceilingEntry(range.getStartOffset());
      assert first != null;

      SortedMap<Integer, Long> interval = treeMap.subMap(first.getKey(), last.getKey());
      for (Map.Entry<Integer, Long> entry : interval.entrySet()) {
        if (entry.getValue() >= boundTime) {
          return ThreeState.YES;
        }
      }
      return ThreeState.NO;
    }
    return ThreeState.UNSURE;
  }

  @Override
  public void register(VirtualFile vf, VcsKey vcsKey, VcsRevisionNumber number, FileAnnotation fa) {
    HistoryCacheWithRevisionKey key = new HistoryCacheWithRevisionKey(VcsContextFactory.getInstance().createFilePathOn(vf), vcsKey, number);
    if (cache.getIfPresent(key) != null) {
      return;
    }

    long absoluteLimit = System.currentTimeMillis() - VcsContentAnnotationSettings.ourAbsoluteLimit;
    TreeMap<Integer, Long> map = new TreeMap<>();
    final int lineCount = fa.getLineCount();
    for (int i = 0; i < lineCount; i++) {
      Date lineDate = fa.getLineDate(i);
      if (lineDate == null) {
        return;
      }
      if (lineDate.getTime() >= absoluteLimit) {
        map.put(i, lineDate.getTime());
      }
    }
    cache.put(key, map);
  }
}
