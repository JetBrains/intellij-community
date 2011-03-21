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
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.annotate.VcsAnnotation;
import com.intellij.util.containers.SLRUMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author irengrig
 *         Date: 2/28/11
 *         Time: 6:55 PM
 */
public class VcsHistoryCache {
  private final Object myLock;
  private final SLRUMap<BaseKey, CachedHistory> myHistoryCache;
  private final SLRUMap<WithRevisionKey, VcsAnnotation> myAnnotationCache;
  //private final SLRUMap<WithRevisionKey, String> myContentCache;

  public VcsHistoryCache() {
    myLock = new Object();
    myHistoryCache = new SLRUMap<BaseKey, CachedHistory>(10, 10);
    myAnnotationCache = new SLRUMap<WithRevisionKey, VcsAnnotation>(10, 5);
    //myContentCache = new SLRUMap<WithRevisionKey, String>(20, 20);
  }

  public <C extends Serializable, T extends VcsAbstractHistorySession> void put(final FilePath filePath,
                                                                                @Nullable final FilePath correctedPath,
                                                                                final VcsKey vcsKey,
                                                                                final T session,
                                                                                @NotNull final VcsCacheableHistorySessionFactory<C, T> factory,
                                                                                boolean isFull) {
    synchronized (myLock) {
      myHistoryCache.put(new BaseKey(filePath, vcsKey),
                         new CachedHistory(correctedPath != null ? correctedPath : filePath, session.getRevisionList(),
                                           session.getCurrentRevisionNumber(), factory.getAddinionallyCachedData(session), isFull));
    }
  }

  @Nullable
  public <C extends Serializable, T extends VcsAbstractHistorySession> T getFull(final FilePath filePath, final VcsKey vcsKey,
                                                                                 @NotNull final VcsCacheableHistorySessionFactory<C, T> factory) {
    synchronized (myLock) {
      final CachedHistory cachedHistory = myHistoryCache.get(new BaseKey(filePath, vcsKey));
      if (cachedHistory == null || ! cachedHistory.isIsFull()) {
        return null;
      }
      return factory.createFromCachedData((C) cachedHistory.getCustomData(), cachedHistory.getRevisions(), cachedHistory.getPath(),
                                          cachedHistory.getCurrentRevision());
    }
  }

  @Nullable
  public <C extends Serializable, T extends VcsAbstractHistorySession> T getMaybePartial(final FilePath filePath, final VcsKey vcsKey,
                                                                                         @NotNull final VcsCacheableHistorySessionFactory<C, T> factory) {
    synchronized (myLock) {
      final CachedHistory cachedHistory = myHistoryCache.get(new BaseKey(filePath, vcsKey));
      if (cachedHistory == null) {
        return null;
      }
      return factory.createFromCachedData((C) cachedHistory.getCustomData(), cachedHistory.getRevisions(), cachedHistory.getPath(),
                                          cachedHistory.getCurrentRevision());
    }
  }

  public void clear() {
    synchronized (myLock) {
      final Iterator<Map.Entry<BaseKey,CachedHistory>> iterator = myHistoryCache.entrySet().iterator();
      while (iterator.hasNext()) {
        final Map.Entry<BaseKey, CachedHistory> next = iterator.next();
        if (! next.getKey().getFilePath().isNonLocal()) {
          iterator.remove();
        }
      }
    }
  }

  public void put(@NotNull final FilePath filePath, @NotNull final VcsKey vcsKey, @NotNull final VcsRevisionNumber number,
                  @NotNull final VcsAnnotation vcsAnnotation) {
    synchronized (myLock) {
      myAnnotationCache.put(new WithRevisionKey(filePath, vcsKey, number), vcsAnnotation);
    }
  }

  public VcsAnnotation get(@NotNull final FilePath filePath, @NotNull final VcsKey vcsKey, @NotNull final VcsRevisionNumber number) {
    synchronized (myLock) {
      return myAnnotationCache.get(new WithRevisionKey(filePath, vcsKey, number));
    }
  }

  private static class WithRevisionKey extends BaseKey {
    private final VcsRevisionNumber myRevisionNumber;

    private WithRevisionKey(FilePath filePath, VcsKey vcsKey, @NotNull VcsRevisionNumber revisionNumber) {
      super(filePath, vcsKey);
      myRevisionNumber = revisionNumber;
    }

    public VcsRevisionNumber getRevisionNumber() {
      return myRevisionNumber;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;

      WithRevisionKey that = (WithRevisionKey)o;

      if (!myRevisionNumber.equals(that.myRevisionNumber)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + myRevisionNumber.hashCode();
      return result;
    }
  }

  private static class BaseKey {
    private final FilePath myFilePath;
    private final VcsKey myVcsKey;

    BaseKey(FilePath filePath, VcsKey vcsKey) {
      myFilePath = filePath;
      myVcsKey = vcsKey;
    }

    public FilePath getFilePath() {
      return myFilePath;
    }

    public VcsKey getVcsKey() {
      return myVcsKey;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      BaseKey baseKey = (BaseKey)o;

      if (!myFilePath.equals(baseKey.myFilePath)) return false;
      if (!myVcsKey.equals(baseKey.myVcsKey)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myFilePath.hashCode();
      result = 31 * result + myVcsKey.hashCode();
      return result;
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
