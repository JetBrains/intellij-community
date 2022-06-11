// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.util.indexing.impl.IndexDebugProperties;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

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

  public StorageLockContext(@NotNull FilePageCache filePageCache,
                            boolean useReadWriteLock,
                            boolean cacheChannels) {
    myLock = new ReentrantReadWriteLock();
    myFilePageCache = filePageCache;
    myUseReadWriteLock = useReadWriteLock;
    myCacheChannels = cacheChannels;
  }

  public StorageLockContext(boolean useReadWriteLock,
                            boolean cacheChannels) {
    this(ourDefaultCache, useReadWriteLock, cacheChannels);
  }

  public StorageLockContext(boolean useReadWriteLock) {
    this(ourDefaultCache, useReadWriteLock, false);
  }

  public StorageLockContext() {
    this(ourDefaultCache, false, false);
  }

  boolean useChannelCache() {
    return myCacheChannels;
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
    if (IndexDebugProperties.DEBUG) {
      if (myLock.writeLock().isHeldByCurrentThread()) return;
      throw new IllegalStateException("Must hold StorageLock write lock to access PagedFileStorage");
    }
  }

  @ApiStatus.Internal
  public void checkReadAccess() {
    if (IndexDebugProperties.DEBUG) {
      if (myLock.getReadHoldCount() > 0 || myLock.writeLock().isHeldByCurrentThread()) return;
      throw new IllegalStateException("Must hold StorageLock read lock to access PagedFileStorage");
    }
  }

  void assertUnderSegmentAllocationLock() {
    if (IndexDebugProperties.DEBUG) {
      myFilePageCache.assertUnderSegmentAllocationLock();
    }
  }
}
