// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.contentAnnotation;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.HistoryCacheWithRevisionKey;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.SLRUMap;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class ContentAnnotationCacheImpl implements ContentAnnotationCache {
  private final SLRUMap<HistoryCacheWithRevisionKey, TreeMap<Integer, Long>> myCache;
  private final Object myLock;

  public ContentAnnotationCacheImpl() {
    myLock = new Object();
    myCache = new SLRUMap<>(50, 50);
  }

  @Override
  @Nullable
  public ThreeState isRecent(final VirtualFile vf,
                             final VcsKey vcsKey,
                             final VcsRevisionNumber number,
                             final TextRange range,
                             final long boundTime) {
    TreeMap<Integer, Long> treeMap;
    synchronized (myLock) {
      treeMap = myCache.get(new HistoryCacheWithRevisionKey(VcsContextFactory.getInstance().createFilePathOn(vf), vcsKey, number));
    }
    if (treeMap != null) {
      Map.Entry<Integer, Long> last = treeMap.floorEntry(range.getEndOffset());
      if (last == null || last.getKey() < range.getStartOffset()) return ThreeState.NO;
      Map.Entry<Integer, Long> first = treeMap.ceilingEntry(range.getStartOffset());
      assert first != null;
      final SortedMap<Integer,Long> interval = treeMap.subMap(first.getKey(), last.getKey());
      for (Map.Entry<Integer, Long> entry : interval.entrySet()) {
        if (entry.getValue() >= boundTime) return ThreeState.YES;
      }
      return ThreeState.NO;
    }
    return ThreeState.UNSURE;
  }

  @Override
  public void register(final VirtualFile vf, final VcsKey vcsKey, final VcsRevisionNumber number, final FileAnnotation fa) {
    final HistoryCacheWithRevisionKey key = new HistoryCacheWithRevisionKey(VcsContextFactory.getInstance().createFilePathOn(vf), vcsKey, number);
    synchronized (myLock) {
      if (myCache.get(key) != null) return;
    }
    final long absoluteLimit = System.currentTimeMillis() - VcsContentAnnotationSettings.ourAbsoluteLimit;
    final TreeMap<Integer, Long> map = new TreeMap<>();
    final int lineCount = fa.getLineCount();
    for (int i = 0; i < lineCount; i++) {
      Date lineDate = fa.getLineDate(i);
      if (lineDate == null) return;
      if (lineDate.getTime() >= absoluteLimit) map.put(i, lineDate.getTime());
    }
    synchronized (myLock) {
      myCache.put(key, map);
    }
  }
}
