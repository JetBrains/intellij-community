// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.io.stats.FilePageCacheStatistics;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@ApiStatus.Internal
public final class StorageLockContext {
  private static final FilePageCache ourDefaultCache = new FilePageCache();

  @NotNull
  private final ReentrantReadWriteLock myLock;
  @NotNull
  private final FilePageCache myFilePageCache;
  private final boolean myUseReadWriteLock;
  private final boolean myCacheChannels;
  private final boolean myDisableAssertions;

  private StorageLockContext(@NotNull FilePageCache filePageCache,
                             boolean useReadWriteLock,
                             boolean cacheChannels,
                             boolean disableAssertions) {
    myLock = new ReentrantReadWriteLock();
    myFilePageCache = filePageCache;
    myUseReadWriteLock = useReadWriteLock;
    myCacheChannels = cacheChannels;
    myDisableAssertions = disableAssertions;
  }

  public StorageLockContext(boolean useReadWriteLock,
                            boolean cacheChannels) {
    this(ourDefaultCache, useReadWriteLock, cacheChannels, false);
  }

  public StorageLockContext(boolean useReadWriteLock,
                            boolean cacheChannels,
                            boolean disableAssertions) {
    this(ourDefaultCache, useReadWriteLock, cacheChannels, disableAssertions);
  }

  public StorageLockContext(boolean useReadWriteLock) {
    this(ourDefaultCache, useReadWriteLock, false, false);
  }

  public StorageLockContext() {
    this(ourDefaultCache, false, false, false);
  }

  boolean useChannelCache() {
    return myCacheChannels;
  }

  public Lock readLock() {
    return myUseReadWriteLock ? myLock.readLock() : myLock.writeLock();
  }
  public Lock writeLock() {
    return myLock.writeLock();
  }

  public void lockRead() {
    if (myUseReadWriteLock) {
      myLock.readLock().lock();
    }
    else {
      myLock.writeLock().lock();
    }
  }

  public void unlockRead() {
    if (myUseReadWriteLock) {
      myLock.readLock().unlock();
    }
    else {
      myLock.writeLock().unlock();
    }
  }

  public void lockWrite() {
    myLock.writeLock().lock();
  }
  public void unlockWrite() {
    myLock.writeLock().unlock();
  }

  @ApiStatus.Internal
  @NotNull
  FilePageCache getBufferCache() {
    return myFilePageCache;
  }

  @ApiStatus.Internal
  public void checkWriteAccess() {
    if (!myDisableAssertions && IndexDebugProperties.DEBUG) {
      if (myLock.writeLock().isHeldByCurrentThread()) return;
      throw new IllegalStateException("Must hold StorageLock write lock to access PagedFileStorage");
    }
  }

  @ApiStatus.Internal
  public void checkReadAccess() {
    if (!myDisableAssertions && IndexDebugProperties.DEBUG) {
      if (myLock.getReadHoldCount() > 0 || myLock.writeLock().isHeldByCurrentThread()) return;
      throw new IllegalStateException("Must hold StorageLock read lock to access PagedFileStorage"); }
  }

  void assertUnderSegmentAllocationLock() {
    if (IndexDebugProperties.DEBUG) {
      myFilePageCache.assertUnderSegmentAllocationLock();
    }
  }

  @ApiStatus.Internal
  public static void forceDirectMemoryCache() {
    ourDefaultCache.flushBuffers();
  }

  @ApiStatus.Internal
  public static @NotNull FilePageCacheStatistics getStatistics() {
    return ourDefaultCache.getStatistics();
  }

  @ApiStatus.Internal
  public static void assertNoBuffersLocked() {
    ourDefaultCache.assertNoBuffersLocked();
  }

  @ApiStatus.Internal
  public static long getCacheMaxSize() {
    return ourDefaultCache.getMaxSize();
  }
}
