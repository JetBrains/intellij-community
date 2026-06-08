// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.io.pagecache.impl.PageContentLockingStrategy;
import com.intellij.util.io.pagecache.impl.PageContentLockingStrategy.SharedLockLockingStrategy;
import com.intellij.util.io.stats.FilePageCacheStatistics;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.nio.file.Path;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.intellij.util.io.PageCacheUtils.FILE_PAGE_CACHE_NEW_CAPACITY_BYTES;
import static com.intellij.util.io.PageCacheUtils.FILE_PAGE_CACHE_OLD_CAPACITY_BYTES;
import static com.intellij.util.io.PageCacheUtils.HEAP_CAPACITY_FRACTION;
import static com.intellij.util.io.PageCacheUtils.LOCK_FREE_PAGE_CACHE_ENABLED;

/**
 * Context of storage operations: which file page cache to use, which kind of locking to use, how to cache file
 * channels...
 * <p>
 * RC: 'lock' part historically was important, but is less important now, since for new FilePageCache locking is
 * decided per storage. Probably class should be renamed to just StorageContext at some moment.
 */
@ApiStatus.Internal
public final class StorageLockContext {
  private static final FilePageCache DEFAULT_FILE_PAGE_CACHE = new FilePageCache(FILE_PAGE_CACHE_OLD_CAPACITY_BYTES);
  private static final @Nullable FilePageCacheLockFree DEFAULT_FILE_PAGE_CACHE_NEW = LOCK_FREE_PAGE_CACHE_ENABLED ?
                                                                                     new FilePageCacheLockFree(
                                                                                       FILE_PAGE_CACHE_NEW_CAPACITY_BYTES,
                                                                                       (long)(FILE_PAGE_CACHE_NEW_CAPACITY_BYTES *
                                                                                              HEAP_CAPACITY_FRACTION)
                                                                                     ) : null;

  static final StorageLockContext DEFAULT_CONTEXT = new StorageLockContext(false);


  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

  private final @NotNull FilePageCache legacyFilePageCache;

  /** In general, null if {@link PageCacheUtils#LOCK_FREE_PAGE_CACHE_ENABLED} is false */
  private final @Nullable FilePageCacheLockFree newFilePageCache;
  private final PageContentLockingStrategy defaultPageContentLockingStrategy = new SharedLockLockingStrategy(lock);

  private final boolean useReadWriteLock;
  private final boolean disableAssertions;

  private final @NotNull ChannelsAccessor readOnlyChannelsAccessor;
  private final @NotNull ChannelsAccessor writableChannelsAccessor;


  @ApiStatus.Internal
  public StorageLockContext(boolean useReadWriteLock,
                            @NotNull ChannelsAccessor readOnlyChannelsAccessor,
                            @NotNull ChannelsAccessor writableChannelsAccessor) {
    this(
      DEFAULT_FILE_PAGE_CACHE, DEFAULT_FILE_PAGE_CACHE_NEW,
      useReadWriteLock,
      /*disableAssertions:*/false,
      readOnlyChannelsAccessor,
      writableChannelsAccessor
    );
  }

  @ApiStatus.Internal
  public StorageLockContext(@NotNull FilePageCache legacyFilePageCache,
                            @Nullable FilePageCacheLockFree newFilePageCacheLockFree,
                            boolean useReadWriteLock,
                            boolean disableAssertions,
                            @NotNull ChannelsAccessor readOnlyChannelsAccessor,
                            @NotNull ChannelsAccessor writableChannelsAccessor) {
    this.useReadWriteLock = useReadWriteLock;
    this.disableAssertions = disableAssertions;

    this.legacyFilePageCache = legacyFilePageCache;
    this.newFilePageCache = newFilePageCacheLockFree;

    if (!readOnlyChannelsAccessor.isReadOnly()) {
      throw new IllegalArgumentException("readOnlyAccessor must be read-only: " + readOnlyChannelsAccessor);
    }
    if (writableChannelsAccessor.isReadOnly()) {
      throw new IllegalArgumentException("writableAccessor must be writable: " + writableChannelsAccessor);
    }
    this.readOnlyChannelsAccessor = readOnlyChannelsAccessor;
    this.writableChannelsAccessor = writableChannelsAccessor;
  }

  private StorageLockContext(@NotNull FilePageCache legacyFilePageCache,
                             @Nullable FilePageCacheLockFree newFilePageCacheLockFree,
                             boolean useReadWriteLock,
                             boolean cacheChannels,
                             boolean disableAssertions) {
    this(legacyFilePageCache, newFilePageCacheLockFree,
         useReadWriteLock,
         disableAssertions,
         PageCacheUtils.getChannelsAccessor(cacheChannels, /*readOnly: */true),
         PageCacheUtils.getChannelsAccessor(cacheChannels, /*readOnly: */false)
    );
  }

  @VisibleForTesting
  public StorageLockContext(@Nullable FilePageCacheLockFree newFilePageCache,
                            boolean useReadWriteLock,
                            boolean cacheChannels,
                            boolean disableAssertions) {
    this(DEFAULT_FILE_PAGE_CACHE, newFilePageCache, useReadWriteLock, cacheChannels, disableAssertions);
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
    this(useReadWriteLock, cacheChannels, /*disableAssertions: */false);
  }

  public StorageLockContext(boolean useReadWriteLock) {
    this(useReadWriteLock, false, false);
  }

  public StorageLockContext() {
    this(false, false, false);
  }

  public Lock readLock() {
    return useReadWriteLock ? lock.readLock() : lock.writeLock();
  }

  public Lock writeLock() {
    return lock.writeLock();
  }

  public void lockRead() {
    if (useReadWriteLock) {
      lock.readLock().lock();
    }
    else {
      lock.writeLock().lock();
    }
  }

  public void unlockRead() {
    if (useReadWriteLock) {
      lock.readLock().unlock();
    }
    else {
      lock.writeLock().unlock();
    }
  }

  public void lockWrite() {
    lock.writeLock().lock();
  }

  public void unlockWrite() {
    lock.writeLock().unlock();
  }

  @VisibleForTesting
  public @NotNull FilePageCache getBufferCache() {
    return legacyFilePageCache;
  }

  /** @throws UnsupportedOperationException if new FilePageCache implementation is absent (disabled) */
  public @NotNull FilePageCacheLockFree pageCache() {
    if (newFilePageCache == null) {
      if (LOCK_FREE_PAGE_CACHE_ENABLED) {
        throw new UnsupportedOperationException(
          "lock-free FilePageCache is not available in this storageLockContext."
        );
      }
      throw new UnsupportedOperationException(
        "lock-free FilePageCache is not available: PageCacheUtils.ENABLE_LOCK_FREE_VFS=false."
      );
    }
    return newFilePageCache;
  }

  public @NotNull PageContentLockingStrategy lockingStrategyWithGlobalLock() {
    return defaultPageContentLockingStrategy;
  }

  public void checkWriteAccess() {
    if (!disableAssertions && IndexDebugProperties.DEBUG) {
      if (lock.writeLock().isHeldByCurrentThread()) return;
      throw new IllegalStateException("Must hold StorageLock write lock to access PagedFileStorage");
    }
  }

  public void checkReadAccess() {
    if (!disableAssertions && IndexDebugProperties.DEBUG) {
      if (lock.getReadHoldCount() > 0 || lock.writeLock().isHeldByCurrentThread()) return;
      throw new IllegalStateException("Must hold StorageLock read lock to access PagedFileStorage");
    }
  }

  @ApiStatus.Internal
  public void checkReadLockNotHeld() {
    if (!disableAssertions) {
      if (readLockHolds() > 0) {
        throw new IllegalStateException("StorageLock.readLock must NOT be held here (write lock is about to be taken?)");
      }
    }
  }

  public int readLockHolds() {
    return lock.getReadHoldCount();
  }

  void assertUnderSegmentAllocationLock() {
    if (IndexDebugProperties.DEBUG) {
      legacyFilePageCache.assertUnderSegmentAllocationLock();
    }
  }

  @ApiStatus.Internal
  public @NotNull ChannelsAccessor getChannelsAccessor(boolean readOnly) {
    return readOnly ? readOnlyChannelsAccessor : writableChannelsAccessor;
  }

  @Override
  public String toString() {
    return "StorageLockContext[" +
           "useReadWriteLock: " + useReadWriteLock +
           ", channels: " + readOnlyChannelsAccessor + "/" + writableChannelsAccessor +
           ", disableAssertions: " + disableAssertions +
           ']';
  }

  /** Checks that no cached channel remains for the file in either read-only or writable accessor. */
  public void assertNoOpenChannels(@NotNull Path path) {
    StringBuilder openChannels = new StringBuilder();
    appendOpenChannelDescription(openChannels, "read-only", readOnlyChannelsAccessor, path);
    appendOpenChannelDescription(openChannels, "writable", writableChannelsAccessor, path);
    if (openChannels.length() > 0) {
      throw new AssertionError("Open channels remain for " + path + ":\n" + openChannels);
    }
  }

  private static void appendOpenChannelDescription(@NotNull StringBuilder openChannels,
                                                   @NotNull String accessorMode,
                                                   @NotNull ChannelsAccessor channelsAccessor,
                                                   @NotNull Path path) {
    if (channelsAccessor instanceof DiagnosticChannelsAccessor) {
      String description = ((DiagnosticChannelsAccessor)channelsAccessor).describeCachedChannelOrNull(path);
      if (description != null) {
        openChannels.append(accessorMode).append(" accessor: ").append(description).append('\n');
      }
    }
  }

  public static void forceDirectMemoryCache() {
    DEFAULT_FILE_PAGE_CACHE.flushBuffers();
  }

  public static @NotNull FilePageCacheStatistics getStatistics() {
    return DEFAULT_FILE_PAGE_CACHE.getStatistics();
  }

  public static @Nullable com.intellij.util.io.pagecache.FilePageCacheStatistics getNewCacheStatistics() {
    return DEFAULT_FILE_PAGE_CACHE_NEW != null ? DEFAULT_FILE_PAGE_CACHE_NEW.getStatistics() : null;
  }

  public static void assertNoBuffersLocked() {
    DEFAULT_FILE_PAGE_CACHE.assertNoBuffersLocked();
  }

  public static long getCacheMaxSize() {
    return DEFAULT_FILE_PAGE_CACHE.getMaxSize();
  }

  /** for monitoring purposes only */
  public static ReentrantReadWriteLock defaultContextLock() {
    return DEFAULT_CONTEXT.lock;
  }
}
