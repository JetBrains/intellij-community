// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.io.FileChannelInterruptsRetryer.FileChannelIdempotentOperation;
import com.intellij.util.io.OpenChannelsCache.FileChannelOperation;
import com.intellij.util.io.stats.FilePageCacheStatistics;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.intellij.util.io.PageCacheUtils.*;

@ApiStatus.Internal
public final class StorageLockContext {

  private static final FilePageCache DEFAULT_FILE_PAGE_CACHE = new FilePageCache(FILE_PAGE_CACHE_OLD_CAPACITY_BYTES);
  @Nullable
  private static final FilePageCacheLockFree DEFAULT_FILE_PAGE_CACHE_NEW = PageCacheUtils.LOCK_FREE_VFS_ENABLED ?
                                                                           new FilePageCacheLockFree(FILE_PAGE_CACHE_NEW_CAPACITY_BYTES) :
                                                                           null;

  static final StorageLockContext ourDefaultContext = new StorageLockContext(false);

  private final ReentrantReadWriteLock myLock = new ReentrantReadWriteLock();

  @NotNull
  private final FilePageCache myFilePageCache;

  /** In general, null if {@link PageCacheUtils#LOCK_FREE_VFS_ENABLED} is false */
  @Nullable
  private final FilePageCacheLockFree myFilePageCacheLockFree;

  private final boolean myUseReadWriteLock;
  private final boolean myCacheChannels;
  private final boolean myDisableAssertions;

  @VisibleForTesting
  StorageLockContext(@NotNull FilePageCache filePageCache,
                     @Nullable FilePageCacheLockFree filePageCacheLockFree,
                     boolean useReadWriteLock,
                     boolean cacheChannels,
                     boolean disableAssertions) {
    myFilePageCache = filePageCache;
    myUseReadWriteLock = useReadWriteLock;
    myCacheChannels = cacheChannels;
    myDisableAssertions = disableAssertions;
    myFilePageCacheLockFree = filePageCacheLockFree;
  }

  @VisibleForTesting
  StorageLockContext(@Nullable FilePageCacheLockFree filePageCacheLockFree,
                     boolean useReadWriteLock,
                     boolean cacheChannels,
                     boolean disableAssertions) {
    this(DEFAULT_FILE_PAGE_CACHE, filePageCacheLockFree, useReadWriteLock, cacheChannels, disableAssertions);
  }

  public StorageLockContext(boolean useReadWriteLock,
                            boolean cacheChannels,
                            boolean disableAssertions) {
    this(DEFAULT_FILE_PAGE_CACHE,
         DEFAULT_FILE_PAGE_CACHE_NEW,
         useReadWriteLock, cacheChannels, disableAssertions);
  }

  public StorageLockContext(boolean useReadWriteLock,
                            boolean cacheChannels) {
    this(useReadWriteLock, cacheChannels, false);
  }

  public StorageLockContext(boolean useReadWriteLock) {
    this(useReadWriteLock, false, false);
  }

  public StorageLockContext() {
    this(false, false, false);
  }

  boolean useChannelCache() {
    return myCacheChannels;
  }

  public <R> R executeOp(final Path myFile,
                         final @NotNull FileChannelOperation<R> operation,
                         final boolean readOnly) throws IOException {
    //MAYBE RC: both branches should be encapsulated inside OpenChannelsCache
    //          (and the OpenChannelsCache should be a part of StorageLockContext then)
    if (useChannelCache()) {
      return CHANNELS_CACHE.executeOp(myFile, operation, readOnly);
    }
    else {
      getBufferCache().incrementUncachedFileAccess();
      try (OpenChannelsCache.ChannelDescriptor desc = new OpenChannelsCache.ChannelDescriptor(myFile, readOnly)) {
        return operation.execute(desc.channel());
      }
    }
  }

  public <R> R executeIdempotentOp(final Path file,
                                   final @NotNull FileChannelIdempotentOperation<R> operation,
                                   final boolean readOnly) throws IOException {
    //MAYBE RC: both branches should be encapsulated inside OpenChannelsCache
    //          (and the OpenChannelsCache should be a part of StorageLockContext then)
    if (useChannelCache()) {
      return CHANNELS_CACHE.executeIdempotentOp(file, operation, readOnly);
    }
    else {
      getBufferCache().incrementUncachedFileAccess();
      try (OpenChannelsCache.ChannelDescriptor desc = new OpenChannelsCache.ChannelDescriptor(file, readOnly)) {
        return desc.channel().executeOperation(operation);
      }
    }
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

  /** @throws UnsupportedOperationException if new FilePageCache implementation is absent (disabled) */
  @ApiStatus.Internal
  @NotNull
  public FilePageCacheLockFree pageCache() {
    if (myFilePageCacheLockFree == null) {
      if (PageCacheUtils.LOCK_FREE_VFS_ENABLED) {
        throw new UnsupportedOperationException(
          "lock-free FilePageCache is not available in this storageLockContext."
        );
      }
      throw new UnsupportedOperationException(
        "lock-free FilePageCache is not available: PageCacheUtils.ENABLE_LOCK_FREE_VFS=false."
      );
    }
    return myFilePageCacheLockFree;
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
      throw new IllegalStateException("Must hold StorageLock read lock to access PagedFileStorage");
    }
  }

  void assertUnderSegmentAllocationLock() {
    if (IndexDebugProperties.DEBUG) {
      myFilePageCache.assertUnderSegmentAllocationLock();
    }
  }

  @ApiStatus.Internal
  public static void forceDirectMemoryCache() {
    DEFAULT_FILE_PAGE_CACHE.flushBuffers();
  }

  @ApiStatus.Internal
  public static @NotNull FilePageCacheStatistics getStatistics() {
    return DEFAULT_FILE_PAGE_CACHE.getStatistics();
  }

  @ApiStatus.Internal
  public static void assertNoBuffersLocked() {
    DEFAULT_FILE_PAGE_CACHE.assertNoBuffersLocked();
  }

  @ApiStatus.Internal
  public static long getCacheMaxSize() {
    return DEFAULT_FILE_PAGE_CACHE.getMaxSize();
  }

  /** for monitoring purposes only */
  @ApiStatus.Internal
  public static ReentrantReadWriteLock defaultContextLock() {
    return ourDefaultContext.myLock;
  }
}
