// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SmartList;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.util.containers.hash.LongLinkedHashMap;
import com.intellij.util.io.stats.FilePageCacheStatistics;
import com.intellij.util.lang.CompoundRuntimeException;
import com.intellij.util.system.CpuArch;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

import static com.intellij.util.io.PagedFileStorage.MB;

/**
 * Maintains 'pages' of data (in the form of {@linkplain DirectBufferWrapper}), from file storages
 * {@linkplain PagedFileStorage}. Each page has unique bufferID (bufferKey, bufferIndex) , which is long
 * <br>
 * <pre>
 * BUFFER_ID(64b) := (BUFFER_OWNING_STORAGE_ID(32b) << 32) | OFFSET_IN_OWNING_STORAGE(32b)
 * </pre>
 * <br>
 * <br>
 * StorageID is unique generated on {@linkplain #registerPagedFileStorage(PagedFileStorage)}. For
 * convenience, storageID is returned as long (instead of int, which it really is), with all meaningful
 * bits already shifted to higher 32b range. This way bufferID could be calculated as just
 * {@code (storageId | offset)}
 * <br>
 * <br>
 * Page cache keeps limit on number of pages being cached: {@linkplain #cachedSizeLimit}. As the limit is
 * reached, the oldest pages are evicted from cache (and flushed to disk). Pages also evicted with their owner
 * {@linkplain PagedFileStorage} re-registration {@linkplain #removeStorage(long)}
 * <p>
 */
@ApiStatus.Internal
final class FilePageCache {
  private static final Logger LOG = Logger.getInstance(FilePageCache.class);

  static final long MAX_PAGES_COUNT = 0xFFFF_FFFFL;
  private static final long FILE_INDEX_MASK = 0xFFFF_FFFF_0000_0000L;

  /**
   * Total size of the cache, bytes
   * It is somewhere ~100-500 Mb, or see static initializer for exact logic
   */
  private static final int CACHE_SIZE;
  static final int ALLOCATOR_SIZE;
  /**
   * 10Mb default, or SystemProperty('idea.max.paged.storage.cache')Mb, but not less than 1 Mb
   */
  static final int DEFAULT_PAGE_SIZE;

  static {
    final int defaultPageSizeMb = SystemProperties.getIntProperty("idea.paged.storage.page.size", 10);
    DEFAULT_PAGE_SIZE = Math.max(1, defaultPageSizeMb) * MB;

    final int lowerCacheSizeMb = 100;
    final int upperCacheSizeMb = CpuArch.is32Bit() ? 200 : 500;

    final long maxDirectMemoryToUse = maxDirectMemory() - 2L * DEFAULT_PAGE_SIZE;

    //RC: basically, try to allocate cache of sys("idea.max.paged.storage.cache", default: upperCacheSizeMb) size,
    //    but not less than lowerCacheSizeMb,
    //    and not more than maxDirectMemoryToUse (strictly)

    final int lowerLimit = (int)Math.min(lowerCacheSizeMb * MB, maxDirectMemoryToUse);
    final int cacheSizeMb = SystemProperties.getIntProperty("idea.max.paged.storage.cache", upperCacheSizeMb);
    CACHE_SIZE = (int)Math.min(
      Math.max(lowerLimit, cacheSizeMb * MB),
      maxDirectMemoryToUse
    );
    ALLOCATOR_SIZE = (int)Math.min(
      100 * MB,
      Math.max(0, maxDirectMemoryToUse - CACHE_SIZE - 300 * MB)
    );
  }


  /**
   * storageId -> storage
   */
  //@GuardedBy("storageById")
  private final Int2ObjectMap<PagedFileStorage> storageById = new Int2ObjectOpenHashMap<>();

  /**
   * In cases there both pagesAllocationLock and pagesAccessLock need to be acquired, pagesAllocationLock
   * should be acquired first
   */
  private final ReentrantLock pagesAccessLock = new ReentrantLock();

  /**
   * Protects .pagesToRemoveByPageId, new pages allocations.
   * Needed for LRU order, totalCacheSize and myMappingChangeCount
   * todo avoid locking for access
   */
  private final ReentrantLock pagesAllocationLock = new ReentrantLock();


  /**
   * pageId (storageId|pageOffsetInStorage) -> page ({@link DirectBufferWrapper})
   */
  //@GuardedBy("pagesAccessLock")
  private final LongLinkedHashMap<DirectBufferWrapper> pagesByPageId;

  //@GuardedBy("pagesAllocationLock")
  private final LinkedHashMap<Long, DirectBufferWrapper> pagesToRemoveByPageId = new LinkedHashMap<>();

  private final long cachedSizeLimit;
  /** Total size of all pages currently cached (i.e. in .pagesByPageId, not in .pagesToRemoveByPageId), bytes */
  private long totalSizeCached;


  //stats counters:

  /** how many times file channel was accessed bypassing cache (see {@link PagedFileStorage#useChannel}) */
  private volatile int myUncachedFileAccess;
  /** page found in local PagedFileStorage cache */
  private int myFastCacheHits;
  /** page found in this cache */
  private int myHits;
  /** page wasn't cached, and load by evicting other page (i.e. cache is full) */
  private int myMisses;
  /** page wasn't cached, and load anew (i.e. cache is not full yet) */
  private int myLoad;

  /** max size of all pages, since application start */
  private long myMaxLoadedSize;
  /** max number of all files (PagedFileStorage), since application start */
  private volatile int myMaxRegisteredFiles;
  private volatile int myMappingChangeCount;

  private long myCreatedCount;
  private long myCreatedMs;
  private long myDisposalMs;


  FilePageCache() {
    cachedSizeLimit = CACHE_SIZE;

    // super hot-spot, it's very essential to use specialized collection here
    pagesByPageId = new LongLinkedHashMap<DirectBufferWrapper>(10, 0.75f, /*access order: */ true) {
      @Override
      protected boolean removeEldestEntry(LongLinkedHashMap.Entry<DirectBufferWrapper> eldest) {
        assert pagesAccessLock.isHeldByCurrentThread();
        return totalSizeCached > cachedSizeLimit;
      }

      @Override
      public DirectBufferWrapper put(long key, @NotNull DirectBufferWrapper wrapper) {
        totalSizeCached += wrapper.getLength();
        DirectBufferWrapper oldShouldBeNull = super.put(key, wrapper);
        myMaxLoadedSize = Math.max(myMaxLoadedSize, totalSizeCached);
        return oldShouldBeNull;
      }

      @Nullable
      @Override
      public DirectBufferWrapper remove(long key) {
        assert pagesAccessLock.isHeldByCurrentThread();
        // this method can be called after removeEldestEntry
        DirectBufferWrapper wrapper = super.remove(key);
        if (wrapper != null) {
          //noinspection NonAtomicOperationOnVolatileField
          ++myMappingChangeCount;
          pagesToRemoveByPageId.put(key, wrapper);
          totalSizeCached -= wrapper.getLength();
        }
        return wrapper;
      }
    };
  }

  public DirectBufferWrapper get(long pageId, boolean read, boolean checkAccess) throws IOException {
    DirectBufferWrapper wrapper;
    //fast path: buffer is in .segments
    //FIXME RC: only read lock is needed here -- could improve scalability for fast path
    //          ...But LinkedHashMap.get() is not a read operation, since with .accessOrder .get() will
    //          reorder entries, hence it is a _write_ operation, that is why exclusive lock
    //          is needed
    pagesAccessLock.lock();
    try {
      wrapper = pagesByPageId.get(pageId);
      if (wrapper != null) {
        myHits++;
        return wrapper;
      }
    }
    finally {
      pagesAccessLock.unlock();
    }

    //maybe buffer is scheduled for remove, but not yet removed? Return it from trash when:
    pagesAllocationLock.lock();
    try {
      DirectBufferWrapper notYetRemoved = pagesToRemoveByPageId.remove(pageId);
      if (notYetRemoved != null) {
        pagesAccessLock.lock();
        try {
          DirectBufferWrapper previous = pagesByPageId.put(pageId, notYetRemoved);
          assert previous == null;
        }
        finally {
          pagesAccessLock.unlock();
        }

        disposeRemovedSegments(null);
        myHits++;
        return notYetRemoved;
      }

      //Double-check: maybe somebody already load our segment after we've checked first time:
      pagesAccessLock.lock();
      try {
        wrapper = pagesByPageId.get(pageId);
        if (wrapper != null) return wrapper;
      }
      finally {
        pagesAccessLock.unlock();
      }

      //Slow path: allocate new buffer and load its content from fileStorage:

      long started = IOStatistics.DEBUG ? System.currentTimeMillis() : 0;

      PagedFileStorage fileStorage = getRegisteredPagedFileStorageByIndex(pageId);
      disposeRemovedSegments(null);

      long disposed = IOStatistics.DEBUG ? System.currentTimeMillis() : 0;

      wrapper = allocateAndLoadPage(pageId, read, fileStorage, checkAccess);

      if (IOStatistics.DEBUG) {
        long finished = System.currentTimeMillis();
        myCreatedCount++;
        myCreatedMs += (finished - disposed);
        myDisposalMs += (disposed - started);
      }

      pagesAccessLock.lock();
      try {
        if (totalSizeCached + fileStorage.myPageSize < cachedSizeLimit) {
          myLoad++;
        }
        else {
          myMisses++;
        }
        pagesByPageId.put(pageId, wrapper);
      }
      finally {
        pagesAccessLock.unlock();
      }

      ensureSize(cachedSizeLimit);

      return wrapper;
    }
    finally {
      pagesAllocationLock.unlock();
    }
  }

  @SuppressWarnings("NonAtomicOperationOnVolatileField") // expected, we don't need 100% precision
  public void incrementUncachedFileAccess() {
    myUncachedFileAccess++;
  }

  public void incrementFastCacheHitsCount() {
    myFastCacheHits++;
  }

  public long getMaxSize() {
    return cachedSizeLimit;
  }

  void unmapBuffersForOwner(PagedFileStorage fileStorage) {
    Map<Long, DirectBufferWrapper> buffers = getBuffersForOwner(fileStorage);

    if (!buffers.isEmpty()) {
      pagesAccessLock.lock();
      try {
        for (Long key : buffers.keySet()) {
          pagesByPageId.remove(key);
        }
      }
      finally {
        pagesAccessLock.unlock();
      }
    }

    pagesAllocationLock.lock();
    try {
      disposeRemovedSegments(fileStorage);
    }
    finally {
      pagesAllocationLock.unlock();
    }
  }

  void flushBuffers() {
    pagesAccessLock.lock();
    try {
      while (!pagesByPageId.isEmpty()) {
        pagesByPageId.doRemoveEldestEntry();
      }
    }
    finally {
      pagesAccessLock.unlock();
    }

    pagesAllocationLock.lock();
    try {
      disposeRemovedSegments(null);
    }
    finally {
      pagesAllocationLock.unlock();
    }
  }

  void flushBuffersForOwner(PagedFileStorage storage) throws IOException {
    storage.getStorageLockContext().checkReadAccess();
    Map<Long, DirectBufferWrapper> buffers = getBuffersForOwner(storage);

    if (!buffers.isEmpty()) {
      List<IOException> exceptions = new SmartList<>();

      pagesAllocationLock.lock();
      try {
        try {
          for (DirectBufferWrapper buffer : buffers.values()) {
            if (buffer.isDirty() && !buffer.isReleased()) {
              buffer.force();
            }
          }
        }
        catch (IOException e) {
          exceptions.add(e);
        }
      }
      finally {
        pagesAllocationLock.unlock();
      }

      if (!exceptions.isEmpty()) {
        throw new IOException(new CompoundRuntimeException(exceptions));
      }
    }
  }

  void removeStorage(final long storageId) {
    synchronized (storageById) {
      storageById.remove((int)(storageId >> 32));
    }
  }

  void assertNoBuffersLocked() {
    pagesAllocationLock.lock();
    try {
      pagesAccessLock.lock();
      try {
        for (DirectBufferWrapper value : pagesByPageId.values()) {
          if (value.isLocked()) {
            throw new AssertionError();
          }
        }
        for (DirectBufferWrapper value : pagesToRemoveByPageId.values()) {
          if (value.isLocked()) {
            throw new AssertionError();
          }
        }
      }
      finally {
        pagesAccessLock.unlock();
      }
    }
    finally {
      pagesAllocationLock.unlock();
    }
  }

  void assertUnderSegmentAllocationLock() {
    assert pagesAllocationLock.isHeldByCurrentThread();
  }

  /**
   * @return unique 'key' (index, id) for newly registered storage. Key is a long with 32 lowest (least
   * significant) bits all 0, and upper 32 bits containing unique storage id.
   */
  long registerPagedFileStorage(@NotNull PagedFileStorage storage) {
    synchronized (storageById) {
      //FIXME RC: why no check for !registered yet? Could be registered twice with different id

      //Generate unique 'id' (index) for a new storage: just find the number not occupied yet. Assume
      // storages rarely closed, so start with currently registered storages count, and count up until
      // 'index' is not in use yet:
      int storageIndex = storageById.size();
      while (storageById.get(storageIndex) != null) {
        storageIndex++;
      }
      storageById.put(storageIndex, storage);
      myMaxRegisteredFiles = Math.max(myMaxRegisteredFiles, storageById.size());
      return (long)storageIndex << 32;
    }
  }

  @NotNull
  FilePageCacheStatistics getStatistics() {
    pagesAllocationLock.lock();
    try {
      pagesAccessLock.lock();
      try {
        return new FilePageCacheStatistics(PagedFileStorage.CHANNELS_CACHE.getStatistics(),
                                           myUncachedFileAccess,
                                           myMaxRegisteredFiles,
                                           myMaxLoadedSize,
                                           totalSizeCached,
                                           myHits,
                                           myFastCacheHits,
                                           myMisses,
                                           myLoad,
                                           myMappingChangeCount,
                                           cachedSizeLimit);
      }
      finally {
        pagesAccessLock.unlock();
      }
    }
    finally {
      pagesAllocationLock.unlock();
    }
  }

  /* ======================= implementation ==================================================================================== */

  private static long maxDirectMemory() {
    try {
      Class<?> aClass = Class.forName("jdk.internal.misc.VM");
      Method maxDirectMemory = aClass.getMethod("maxDirectMemory");
      return (Long)maxDirectMemory.invoke(null);
    }
    catch (Throwable ignore) {
    }

    try {
      Class<?> aClass = Class.forName("sun.misc.VM");
      Method maxDirectMemory = aClass.getMethod("maxDirectMemory");
      return (Long)maxDirectMemory.invoke(null);
    }
    catch (Throwable ignore) {
    }

    try {
      Class<?> aClass = Class.forName("java.nio.Bits");
      Field maxMemory = aClass.getDeclaredField("maxMemory");
      maxMemory.setAccessible(true);
      return (Long)maxMemory.get(null);
    }
    catch (Throwable ignore) {
    }

    try {
      Class<?> aClass = Class.forName("java.nio.Bits");
      Field maxMemory = aClass.getDeclaredField("MAX_MEMORY");
      maxMemory.setAccessible(true);
      return (Long)maxMemory.get(null);
    }
    catch (Throwable ignore) {
    }

    return Runtime.getRuntime().maxMemory();
  }

  @NotNull("Seems accessed storage has been closed")
  private PagedFileStorage getRegisteredPagedFileStorageByIndex(long storageId) {
    int storageIndex = (int)((storageId & FILE_INDEX_MASK) >> 32);
    synchronized (storageById) {
      return storageById.get(storageIndex);
    }
  }

  private void disposeRemovedSegments(@Nullable PagedFileStorage verificationStorage) {
    assertUnderSegmentAllocationLock();

    if (pagesToRemoveByPageId.isEmpty()) return;
    Iterator<Map.Entry<Long, DirectBufferWrapper>> iterator = pagesToRemoveByPageId.entrySet().iterator();
    while (iterator.hasNext()) {
      try {
        Map.Entry<Long, DirectBufferWrapper> entry = iterator.next();
        DirectBufferWrapper wrapper = entry.getValue();
        boolean released = wrapper.tryRelease(wrapper.getFile() == verificationStorage);

        if (released) {
          iterator.remove();
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  private void ensureSize(long sizeLimit) {
    pagesAllocationLock.isHeldByCurrentThread();

    pagesAccessLock.lock();
    try {
      while (totalSizeCached > sizeLimit) {
        // we still have to drop something
        pagesByPageId.doRemoveEldestEntry();
      }
    }
    finally {
      pagesAccessLock.unlock();
    }

    disposeRemovedSegments(null);
  }

  @NotNull
  private static DirectBufferWrapper allocateAndLoadPage(long pageId, boolean read, PagedFileStorage owner, boolean checkAccess)
    throws IOException {
    if (checkAccess) {
      StorageLockContext context = owner.getStorageLockContext();
      if (read) {
        context.checkReadAccess();
      }
      else {
        context.checkWriteAccess();
      }
    }
    final long offsetInFile = (pageId & MAX_PAGES_COUNT) * owner.myPageSize;

    return new DirectBufferWrapper(owner, offsetInFile);
  }

  @NotNull
  private Map<Long, DirectBufferWrapper> getBuffersForOwner(@NotNull PagedFileStorage storage) {
    StorageLockContext storageLockContext = storage.getStorageLockContext();
    pagesAccessLock.lock();
    try {
      storageLockContext.checkReadAccess();
      Map<Long, DirectBufferWrapper> mineBuffers = new TreeMap<>();
      for (LongLinkedHashMap.Entry<DirectBufferWrapper> entry : pagesByPageId.entrySet()) {
        if (entry.getValue().getFile() == storage) {
          mineBuffers.put(entry.getKey(), entry.getValue());
        }
      }
      return mineBuffers;
    }
    finally {
      pagesAccessLock.unlock();
    }
  }
}
