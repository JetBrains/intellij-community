/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

/**
* Created by IntelliJ IDEA.
* User: Irina.Chernushina
* Date: 8/8/11
* Time: 8:26 PM
*/
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
      treeMap = myCache.get(new HistoryCacheWithRevisionKey(VcsContextFactory.SERVICE.getInstance().createFilePathOn(vf), vcsKey, number));
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
    final HistoryCacheWithRevisionKey key = new HistoryCacheWithRevisionKey(VcsContextFactory.SERVICE.getInstance().createFilePathOn(vf), vcsKey, number);
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
