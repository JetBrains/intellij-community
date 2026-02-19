// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SmartList;
import com.intellij.util.containers.hash.LongLinkedHashMap;
import com.intellij.util.io.stats.FilePageCacheStatistics;
import com.intellij.util.lang.CompoundRuntimeException;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

import static com.intellij.util.SystemProperties.getBooleanProperty;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

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
public final class FilePageCache {
  private static final Logger LOG = Logger.getInstance(FilePageCache.class);
  //@formatter:off
  /**
   * By default, we throw errors, since it is almost 100% incorrect and buggy to create >1 storage over the same file.
   * But some legacy code relies on that (RIDER-100680), and for backward-compatibility one could set the flag to false,
   * returning to the old behavior there it was allowed to have >1 storage (and exception is logged as warning).
   * This is only a temporary, short-term solution, because it just suppresses error, which almost always manifests
   * itself later as data corruption.
   * Beware: 99%+ data corruption risks are on you then.
   */
  private static final boolean THROW_ERROR_ON_DUPLICATE_STORAGE_REGISTRATION = getBooleanProperty("FilePageCache.THROW_ERROR_ON_DUPLICATE_STORAGE_REGISTRATION", true);
  private static final boolean KEEP_STACK_TRACE_AT_STORAGE_REGISTRATION = getBooleanProperty("FilePageCache.KEEP_STACK_TRACE_AT_STORAGE_REGISTRATION", true);
  //@formatter:on

  /**
   * Measure times of page loading/disposing.
   *
   * @see #myPageLoadUs
   * @see #myPageDisposalUs
   */
  private static final boolean COLLECT_PAGE_LOADING_TIMES = true;//IOStatistics.DEBUG;

  static final long MAX_PAGES_COUNT = 0xFFFF_FFFFL;
  private static final long FILE_INDEX_MASK = 0xFFFF_FFFF_0000_0000L;

  /** storageId -> storage */
  //@GuardedBy("storageById")
  private final Int2ObjectMap<PagedFileStorage> storageById = new Int2ObjectOpenHashMap<>();
  //@GuardedBy("storageById")
  private final Map<Path, Exception> stackTracesOfStorageRegistration = KEEP_STACK_TRACE_AT_STORAGE_REGISTRATION ? new HashMap<>() : null;
  //@GuardedBy("storageById")
  private final Map<Path, PagedFileStorage> storageByAbsolutePath = new HashMap<>();

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
  private final Long2ObjectLinkedOpenHashMap<DirectBufferWrapper> pagesToRemoveByPageId = new Long2ObjectLinkedOpenHashMap<>();

  private final long cachedSizeLimit;
  /** Total size of all pages currently cached (i.e. in .pagesByPageId, not in .pagesToRemoveByPageId), bytes */
  private long totalSizeCached;


  //stats counters:

  /** how many times a file channel was accessed bypassing cache (see {@link PagedFileStorage#executeOp}) */
  private volatile int myUncachedFileAccess;
  /** How many times page was found in local PagedFileStorage cache */
  private int myFastCacheHits;
  /** How many times page was found in this cache */
  private int myHits;
  /** How many pages were loaded so totalSizeCached become above the cacheCapacityBytes */
  private int myPageLoadsAboveSizeThreshold;
  /** How many pages were loaded without overthrowing cache capacity (cacheCapacityBytes) */
  private int myRegularPageLoads;

  /** max(totalSizeCached), since application start -- i.e. max size this cache ever reached. */
  private long myMaxLoadedSize;
  /** max(number of all files (PagedFileStorage)), since application start */
  private volatile int myMaxRegisteredFiles;
  /** How many pages were put into .pagesToRemoveByPageId (RC: not sure what it means exactly) */
  private volatile int myMappingChangeCount;

  /** How many pages were loaded in total. == (myLoads + myMisses) */
  private long myLoadedPages;
  /** Total time (us) of all page loads (including page buffer allocation time) */
  private long myPageLoadUs;
  /**
   * Total time (us) of all page disposals _before reuse_.
   * I.e. it is part of the full waiting time for a new page to be loaded.
   */
  private long myPageDisposalUs;


  FilePageCache(final long cacheCapacityBytes) {
    if (cacheCapacityBytes <= 0) {
      throw new IllegalArgumentException("Capacity(=" + cacheCapacityBytes + ") must be >0");
    }
    cachedSizeLimit = cacheCapacityBytes;

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

      @Override
      public @Nullable DirectBufferWrapper remove(long key) {
        assert pagesAccessLock.isHeldByCurrentThread();
        // this method can be called after removeEldestEntry
        DirectBufferWrapper wrapper = super.remove(key);
        if (wrapper != null) {
          //noinspection NonAtomicOperationOnVolatileField
          myMappingChangeCount++;
          assertUnderSegmentAllocationLock();
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

      //Double-check: maybe somebody already loads our segment after we've checked first time:
      pagesAccessLock.lock();
      try {
        wrapper = pagesByPageId.get(pageId);
        if (wrapper != null) return wrapper;
      }
      finally {
        pagesAccessLock.unlock();
      }

      //Slow path: allocate new buffer and load its content from fileStorage:

      final long startedAtNs = COLLECT_PAGE_LOADING_TIMES ? System.nanoTime() : 0;

      final PagedFileStorage fileStorage = getRegisteredPagedFileStorageByIndex(pageId);
      disposeRemovedSegments(null);

      final long disposeFinishedAtNs = COLLECT_PAGE_LOADING_TIMES ? System.nanoTime() : 0;

      wrapper = allocateAndLoadPage(pageId, read, fileStorage, checkAccess);

      if (COLLECT_PAGE_LOADING_TIMES) {
        final long finishedAtNs = System.nanoTime();
        myLoadedPages++;
        myPageLoadUs += NANOSECONDS.toMicros(finishedAtNs - disposeFinishedAtNs);
        myPageDisposalUs += NANOSECONDS.toMicros(disposeFinishedAtNs - startedAtNs);
      }

      pagesAccessLock.lock();
      try {
        if (totalSizeCached + fileStorage.getPageSize() < cachedSizeLimit) {
          myRegularPageLoads++;
        }
        else {
          myPageLoadsAboveSizeThreshold++;
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

    pagesAllocationLock.lock();
    try {
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

      disposeRemovedSegments(fileStorage);
    }
    finally {
      pagesAllocationLock.unlock();
    }
  }

  void flushBuffers() {
    pagesAllocationLock.lock();
    try {
      pagesAccessLock.lock();
      try {
        while (!pagesByPageId.isEmpty()) {
          pagesByPageId.doRemoveEldestEntry();
        }
      }
      finally {
        pagesAccessLock.unlock();
      }

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
      PagedFileStorage removedStorage = storageById.remove((int)(storageId >> 32));
      if (removedStorage != null) {
        Path storageFile = removedStorage.getFile();
        Path storageAbsolutePath = storageFile.toAbsolutePath();
        storageByAbsolutePath.remove(storageAbsolutePath);
        if (KEEP_STACK_TRACE_AT_STORAGE_REGISTRATION) {
          stackTracesOfStorageRegistration.remove(storageAbsolutePath);
        }
      }
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
      Path storageFile = storage.getFile();
      Path storageAbsolutePath = storageFile.toAbsolutePath();
      PagedFileStorage alreadyRegisteredStorage = storageByAbsolutePath.get(storageAbsolutePath);
      if (alreadyRegisteredStorage != null) {
        IllegalStateException ex = new IllegalStateException(
          "Storage for [" + storageAbsolutePath + "] is already registered"
        );
        if (KEEP_STACK_TRACE_AT_STORAGE_REGISTRATION) {
          Exception stackTraceHolder = stackTracesOfStorageRegistration.get(storageAbsolutePath);
          if (stackTraceHolder != null) {
            ex.addSuppressed(stackTraceHolder);
          }
        }
        if (THROW_ERROR_ON_DUPLICATE_STORAGE_REGISTRATION) {
          throw ex;
        }
        else {
          LOG.warn(ex.getMessage(), ex);
        }
      }

      //Generate unique 'id' (index) for a new storage: just find the number not occupied yet. Assume
      // storages are rarely closed, so start with currently registered storages count, and count up
      // until 'index' is not in use yet:
      int storageIndex = storageById.size();
      while (storageById.get(storageIndex) != null) {
        storageIndex++;
      }
      storageById.put(storageIndex, storage);
      storageByAbsolutePath.put(storageAbsolutePath, storage);
      if (KEEP_STACK_TRACE_AT_STORAGE_REGISTRATION) {
        stackTracesOfStorageRegistration.put(storageAbsolutePath,
                                             new Exception("Storage[" + storageAbsolutePath + "] registration stack trace"));
      }
      myMaxRegisteredFiles = Math.max(myMaxRegisteredFiles, storageById.size());
      return (long)storageIndex << 32;
    }
  }

  @VisibleForTesting
  public @NotNull FilePageCacheStatistics getStatistics() {
    pagesAllocationLock.lock();
    try {
      pagesAccessLock.lock();
      try {
        return new FilePageCacheStatistics(PageCacheUtils.CHANNELS_CACHE.getStatistics(),
                                           myUncachedFileAccess,
                                           myMaxRegisteredFiles,
                                           myMaxLoadedSize,
                                           totalSizeCached,
                                           myHits,
                                           myFastCacheHits,
                                           myPageLoadsAboveSizeThreshold,
                                           myRegularPageLoads,
                                           myMappingChangeCount,
                                           myPageDisposalUs,
                                           myPageLoadUs,
                                           myLoadedPages,
                                           cachedSizeLimit
        );
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

  private @NotNull("Seems accessed storage has been closed") PagedFileStorage getRegisteredPagedFileStorageByIndex(long storageId)
    throws ClosedStorageException {
    int storageIndex = (int)((storageId & FILE_INDEX_MASK) >> 32);
    synchronized (storageById) {
      PagedFileStorage storage = storageById.get(storageIndex);
      if (storage == null) {
        throw new ClosedStorageException("storage is already closed");
      }
      return storage;
    }
  }

  private void disposeRemovedSegments(@Nullable PagedFileStorage verificationStorage) {
    assertUnderSegmentAllocationLock();

    if (pagesToRemoveByPageId.isEmpty()) {
      return;
    }

    ObjectIterator<DirectBufferWrapper> iterator = pagesToRemoveByPageId.values().iterator();
    while (iterator.hasNext()) {
      try {
        DirectBufferWrapper wrapper = iterator.next();
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
    assert pagesAllocationLock.isHeldByCurrentThread();

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

  private static @NotNull DirectBufferWrapper allocateAndLoadPage(long pageId, boolean read, PagedFileStorage owner, boolean checkAccess)
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
    final long offsetInFile = (pageId & MAX_PAGES_COUNT) * owner.getPageSize();

    return new DirectBufferWrapper(owner, offsetInFile);
  }

  private @NotNull Map<Long, DirectBufferWrapper> getBuffersForOwner(@NotNull PagedFileStorage storage) {
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
