// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.util.Consumer;
import com.intellij.util.containers.SLRUMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author irengrig
 */
public class VcsHistoryCache {
  private final Object myLock;
  private final SLRUMap<HistoryCacheBaseKey, CachedHistory> myHistoryCache;
  private final SLRUMap<HistoryCacheWithRevisionKey, Object> myAnnotationCache;
  private final SLRUMap<HistoryCacheWithRevisionKey, VcsRevisionNumber> myLastRevisionCache;

  public VcsHistoryCache() {
    myLock = new Object();
    // increase cache size when preload enabled
    boolean preloadEnabled = AdvancedSettings.getBoolean("vcs.annotations.preload") || Registry.is("vcs.code.author.inlay.hints");
    myHistoryCache = new SLRUMap<>(
      preloadEnabled ? 50 : 10,
      preloadEnabled ? 50 : 10);
    myAnnotationCache = new SLRUMap<>(
      preloadEnabled ? 50 : 10,
      preloadEnabled ? 50 : 5);
    myLastRevisionCache = new SLRUMap<>(50, 50);
  }

  public <C extends Serializable, T extends VcsAbstractHistorySession> void put(
    @NotNull FilePath filePath,
    @Nullable FilePath correctedPath,
    @NotNull VcsKey vcsKey,
    @NotNull T session,
    @NotNull VcsCacheableHistorySessionFactory<C, T> factory,
    boolean isFull
  ) {
    synchronized (myLock) {
      myHistoryCache.put(new HistoryCacheBaseKey(filePath, vcsKey),
                         new CachedHistory(correctedPath != null ? correctedPath : filePath, session.getRevisionList(),
                                           session.getCurrentRevisionNumber(), factory.getAdditionallyCachedData(session), isFull));
    }
  }

  public void editCached(@NotNull FilePath filePath, @NotNull VcsKey vcsKey, @NotNull Consumer<? super List<VcsFileRevision>> consumer) {
    synchronized (myLock) {
      CachedHistory cachedHistory = myHistoryCache.get(new HistoryCacheBaseKey(filePath, vcsKey));
      if (cachedHistory != null) {
        consumer.consume(cachedHistory.getRevisions());
      }
    }
  }

  @Nullable
  public <C extends Serializable, T extends VcsAbstractHistorySession> T getFull(
    @NotNull FilePath filePath,
    @NotNull VcsKey vcsKey,
    @NotNull VcsCacheableHistorySessionFactory<C, T> factory
  ) {
    synchronized (myLock) {
      CachedHistory cachedHistory = myHistoryCache.get(new HistoryCacheBaseKey(filePath, vcsKey));
      if (cachedHistory == null || !cachedHistory.isIsFull()) {
        return null;
      }
      //noinspection unchecked
      C customData = (C)cachedHistory.getCustomData();
      return factory.createFromCachedData(customData, cachedHistory.getRevisions(), cachedHistory.getPath(),
                                          cachedHistory.getCurrentRevision());
    }
  }

  @Nullable
  public <C extends Serializable, T extends VcsAbstractHistorySession> T getMaybePartial(
    @NotNull FilePath filePath,
    @NotNull VcsKey vcsKey,
    @NotNull VcsCacheableHistorySessionFactory<C, T> factory
  ) {
    synchronized (myLock) {
      CachedHistory cachedHistory = myHistoryCache.get(new HistoryCacheBaseKey(filePath, vcsKey));
      if (cachedHistory == null) {
        return null;
      }
      //noinspection unchecked
      C customData = (C)cachedHistory.getCustomData();
      return factory.createFromCachedData(customData, cachedHistory.getRevisions(), cachedHistory.getPath(),
                                          cachedHistory.getCurrentRevision());
    }
  }

  public void clearAll() {
    clearHistory();
    clearAnnotations();
    clearLastRevisions();
  }

  public void clearHistory() {
    synchronized (myLock) {
      Iterator<Map.Entry<HistoryCacheBaseKey, CachedHistory>> iterator = myHistoryCache.entrySet().iterator();
      while (iterator.hasNext()) {
        Map.Entry<HistoryCacheBaseKey, CachedHistory> next = iterator.next();
        if (!next.getKey().getFilePath().isNonLocal()) {
          iterator.remove();
        }
      }
    }
  }

  public void putAnnotation(@NotNull FilePath filePath, @NotNull VcsKey vcsKey, @NotNull VcsRevisionNumber number,
                            @NotNull Object vcsAnnotation) {
    synchronized (myLock) {
      myAnnotationCache.put(new HistoryCacheWithRevisionKey(filePath, vcsKey, number), vcsAnnotation);
    }
  }

  @Nullable
  public Object getAnnotation(@NotNull FilePath filePath, @NotNull VcsKey vcsKey, @NotNull VcsRevisionNumber number) {
    synchronized (myLock) {
      return myAnnotationCache.get(new HistoryCacheWithRevisionKey(filePath, vcsKey, number));
    }
  }

  public void clearAnnotations() {
    synchronized (myLock) {
      myAnnotationCache.clear();
    }
  }

  public void putLastRevision(@NotNull FilePath filePath, @NotNull VcsKey vcsKey, @NotNull VcsRevisionNumber currentRevision,
                              @NotNull VcsRevisionNumber lastRevision) {
    synchronized (myLock) {
      myLastRevisionCache.put(new HistoryCacheWithRevisionKey(filePath, vcsKey, currentRevision), lastRevision);
    }
  }

  @Nullable
  public VcsRevisionNumber getLastRevision(@NotNull FilePath filePath, @NotNull VcsKey vcsKey, @NotNull VcsRevisionNumber currentRevision) {
    synchronized (myLock) {
      return myLastRevisionCache.get(new HistoryCacheWithRevisionKey(filePath, vcsKey, currentRevision));
    }
  }

  public void clearLastRevisions() {
    synchronized (myLock) {
      myLastRevisionCache.clear();
    }
  }

  public static class CachedHistory {
    private final FilePath myPath;
    private final List<VcsFileRevision> myRevisions;
    private final VcsRevisionNumber myCurrentRevision;
    private final Object myCustomData;
    private final boolean myIsFull;

    public CachedHistory(FilePath path,
                         List<VcsFileRevision> revisions,
                         VcsRevisionNumber currentRevision,
                         Object customData,
                         boolean isFull) {
      myPath = path;
      myRevisions = revisions;
      myCurrentRevision = currentRevision;
      myCustomData = customData;
      myIsFull = isFull;
    }

    public FilePath getPath() {
      return myPath;
    }

    public List<VcsFileRevision> getRevisions() {
      return myRevisions;
    }

    public VcsRevisionNumber getCurrentRevision() {
      return myCurrentRevision;
    }

    public Object getCustomData() {
      return myCustomData;
    }

    public boolean isIsFull() {
      return myIsFull;
    }
  }
}
