// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.FileChannelInterruptsRetryer.FileChannelIdempotentOperation;
import com.intellij.util.io.OpenChannelsCache.FileChannelOperation;
import com.intellij.util.io.pagecache.FilePageCacheStatistics;
import com.intellij.util.io.pagecache.Page;
import com.intellij.util.io.pagecache.PagedStorage;
import com.intellij.util.io.pagecache.impl.*;
import com.intellij.util.io.storage.AbstractStorage;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.intellij.util.SystemProperties.getIntProperty;
import static java.nio.ByteOrder.BIG_ENDIAN;

@Internal
public final class PagedFileStorageWithRWLockedPageContent implements PagedStorage {
  private static final Logger LOG = Logger.getInstance(PagedFileStorageWithRWLockedPageContent.class);

  public static final int DEFAULT_PAGE_SIZE = PageCacheUtils.DEFAULT_PAGE_SIZE;


  private static final StorageLockContext DEFAULT_LOCK_CONTEXT = new StorageLockContext(false);

  public static final ThreadLocal<StorageLockContext> THREAD_LOCAL_STORAGE_LOCK_CONTEXT = new ThreadLocal<>();

  private static final int MAX_ATTEMPTS_TO_ACQUIRE_PAGE = getIntProperty("vfs.lock-free-impl.max-attempts-to-acquire-page", 10_000);


  private final @NotNull StorageLockContext storageLockContext;

  private final @NotNull Path file;

  private final boolean readOnly;

  private final int pageSize;

  private final boolean nativeBytesOrder;

  private final PageContentLockingStrategy pageContentLockingStrategy;

  private final @NotNull FilePageCacheLockFree pageCache;
  /** Cached value of {@link FilePageCacheLockFree#getStatistics()} */
  private final transient FilePageCacheStatistics pageCacheStatistics;

  private final PagesTable pages;

  /**
   * Assigned in {@link #closeAsync()}, tracks the closing process happening in housekeeper thread
   * in the {@link #pageCache}.
   */
  private volatile Future<?> closingInProgress = null;

  private final AtomicInteger dirtyPagesCount = new AtomicInteger(0);
  private final AtomicLong actualSize = new AtomicLong(0);

  private final transient PageToStorageHandle pageToStorageHandle = new PageToStorageHandle() {
    @Override
    public void pageBecomeDirty() {
      dirtyPagesCount.incrementAndGet();
    }

    @Override
    public void pageBecomeClean() {
      final int dirtyPages = dirtyPagesCount.decrementAndGet();
      if (dirtyPages < 0) {
        throw new AssertionError("Bug: dirty pages (=" + dirtyPages + ") can't be negative");
      }
    }

    @Override
    public void modifiedRegionUpdated(final long startOffsetInFile,
                                      final int length) {
      do {//CAS loop:
        final long actualFileSize = actualSize.get();
        final long requiredFileSize = startOffsetInFile + length;
        if (actualFileSize >= requiredFileSize) {
          return;
        }
        if (actualSize.compareAndSet(actualFileSize, requiredFileSize)) {
          return;
        }
      }
      while (true);
    }

    @Override
    public void flushBytes(final @NotNull ByteBuffer dataToFlush,
                           final long offsetInFile) throws IOException {
      flushPage(dataToFlush, offsetInFile);
    }
  };

  public static PagedFileStorageWithRWLockedPageContent createWithDefaults(@NotNull Path file,
                                                                           @Nullable StorageLockContext storageLockContext,
                                                                           int pageSize,
                                                                           boolean nativeBytesOrder,
                                                                           @NotNull PageContentLockingStrategy strategy)
    throws IOException {
    // TODO read-only flag should be extracted from PersistentHashMapValueStorage.CreationTimeOptions
    boolean readOnly = PersistentHashMapValueStorage.CreationTimeOptions.READONLY.get() == Boolean.TRUE;

    StorageLockContext storageContext = findOutAppropriateContext(storageLockContext);

    int actualPageSize = Math.max(pageSize > 0 ? pageSize : PageCacheUtils.DEFAULT_PAGE_SIZE, AbstractStorage.PAGE_SIZE);

    return new PagedFileStorageWithRWLockedPageContent(
      file, storageContext,
      actualPageSize, nativeBytesOrder, readOnly,
      strategy
    );
  }

  public PagedFileStorageWithRWLockedPageContent(@NotNull Path file,
                                                 @NotNull StorageLockContext storageLockContext,
                                                 int pageSize,
                                                 @NotNull PageContentLockingStrategy strategy) throws IOException {
    this(file, storageLockContext, pageSize, /*nativeBytesOrder: */true, /*readOnly: */false, strategy);
  }

  public PagedFileStorageWithRWLockedPageContent(@NotNull Path file,
                                                 @NotNull StorageLockContext storageLockContext,
                                                 int pageSize,
                                                 boolean nativeBytesOrder,
                                                 @NotNull PageContentLockingStrategy strategy) throws IOException {
    this(file, storageLockContext, pageSize, nativeBytesOrder, /*readOnly: */false, strategy);
  }

  public PagedFileStorageWithRWLockedPageContent(@NotNull Path file,
                                                 @NotNull StorageLockContext storageLockContext,
                                                 int pageSize,
                                                 boolean nativeBytesOrder,
                                                 boolean readOnly,
                                                 @NotNull PageContentLockingStrategy strategy) throws IOException {
    if (pageSize <= 0) {
      throw new IllegalArgumentException("pageSize(=" + pageSize + ") must be positive");
    }

    this.file = file;

    this.storageLockContext = storageLockContext;
    this.pageSize = pageSize;
    this.readOnly = readOnly;
    this.nativeBytesOrder = nativeBytesOrder;

    this.pageContentLockingStrategy = strategy;

    pageCache = this.storageLockContext.pageCache();
    pages = pageCache.registerStorage(this);

    try {
      if (Files.exists(file)) {
        actualSize.set(Files.size(file));
      }
    }
    catch (IOException ignored) {
    }
    pageCacheStatistics = pageCache.getStatistics();
  }


  @Override
  public @NotNull Path getFile() {
    return file;
  }

  @Override
  public boolean isReadOnly() {
    return readOnly;
  }

  @Override
  public int getPageSize() {
    return pageSize;
  }

  @Override
  public boolean isNativeBytesOrder() {
    return nativeBytesOrder;
  }


  //======== Accessors:

  @Override
  public void putInt(final long offsetInFile,
                     final int value) throws IOException {
    checkValueIsPageAligned(offsetInFile, Integer.BYTES);

    final int pageIndex = toPageIndex(offsetInFile);
    final int offsetOnPage = toOffsetInPage(offsetInFile);
    try (final Page page = pageByIndex(pageIndex, /*forWrite: */ true)) {
      page.putInt(offsetOnPage, value);
    }
  }

  @Override
  public int getInt(final long offsetInFile) throws IOException {
    checkValueIsPageAligned(offsetInFile, Integer.BYTES);

    final int pageIndex = toPageIndex(offsetInFile);
    final int offsetInPage = toOffsetInPage(offsetInFile);
    try (final Page page = pageByIndex(pageIndex, /*forWrite: */ false)) {
      return page.getInt(offsetInPage);
    }
  }

  @Override
  public void putLong(final long offsetInFile,
                      final long value) throws IOException {
    checkValueIsPageAligned(offsetInFile, Long.BYTES);

    final int pageIndex = toPageIndex(offsetInFile);
    final int offsetInPage = toOffsetInPage(offsetInFile);
    try (final Page page = pageByIndex(pageIndex, /*forWrite: */ true)) {
      page.putLong(offsetInPage, value);
    }
  }

  @Override
  public long getLong(final long offsetInFile) throws IOException {
    checkValueIsPageAligned(offsetInFile, Long.BYTES);

    final int pageIndex = toPageIndex(offsetInFile);
    final int offsetInPage = toOffsetInPage(offsetInFile);
    try (final Page page = pageByIndex(pageIndex, /*forWrite: */ false)) {
      return page.getLong(offsetInPage);
    }
  }

  @Override
  public void putBuffer(final long offsetInFile,
                        final @NotNull ByteBuffer data) throws IOException {
    checkValueIsPageAligned(offsetInFile, data.remaining());

    final int pageIndex = toPageIndex(offsetInFile);
    final int offsetInPage = toOffsetInPage(offsetInFile);
    try (final Page page = pageByIndex(pageIndex, /*forWrite: */ true)) {
      page.putFromBuffer(data, offsetInPage);
    }
  }

  @Override
  public byte get(final long offsetInFile) throws IOException {
    final int pageIndex = toPageIndex(offsetInFile);
    final int offsetInPage = toOffsetInPage(offsetInFile);
    try (final Page page = pageByIndex(pageIndex, /*forWrite: */ false)) {
      return page.get(offsetInPage);
    }
  }

  @Override
  public void put(final long offsetInFile,
                  final byte value) throws IOException {
    final int pageIndex = toPageIndex(offsetInFile);
    final int offsetInPage = toOffsetInPage(offsetInFile);
    try (final Page page = pageByIndex(pageIndex, /*forWrite: */ true)) {
      page.put(offsetInPage, value);
    }
  }

  @Override
  public void get(final long offsetInFile,
                  final byte[] destination, final int offsetInArray, final int length) throws IOException {
    long currentOffsetInFile = offsetInFile;
    int currentOffsetInArray = offsetInArray;
    int remainingBytesToRead = length;

    while (remainingBytesToRead > 0) {
      final int pageIndex = toPageIndex(currentOffsetInFile);
      final int offsetInPage = toOffsetInPage(currentOffsetInFile);

      int bytesToRead = Math.min(remainingBytesToRead, pageSize - offsetInPage);
      try (final Page page = pageByIndex(pageIndex, /*forWrite: */ false)) {
        page.readToArray(destination, currentOffsetInArray, offsetInPage, bytesToRead);
      }

      remainingBytesToRead -= bytesToRead;
      currentOffsetInArray += bytesToRead;
      currentOffsetInFile += bytesToRead;
    }
  }

  @Override
  public void put(final long offsetInFile,
                  final byte[] src, final int offsetInArray, final int length) throws IOException {
    //MAYBE RC: here array could cross page boundary, while for all other methods it is prohibited
    //          which is inconsistent. Prohibit crossing page boundary here also, and move this code
    //          to PagedStorageWithUnalignedAccess?
    long i = offsetInFile;
    int o = offsetInArray;
    int l = length;

    while (l > 0) {
      final int pageIndex = toPageIndex(i);
      final int offsetInPage = toOffsetInPage(i);

      final int page_len = Math.min(l, pageSize - offsetInPage);
      try (final Page page = pageByIndex(pageIndex, /*forWrite: */ true)) {
        page.putFromArray(src, o, offsetInPage, page_len);
      }
      l -= page_len;
      o += page_len;
      i += page_len;
    }
  }

  @Override
  public long length() {
    return actualSize.get();
  }

  @Override
  public void clear() {
    //TODO: how to implement this method?
    //      1. truncate file
    //      2. set actualSize=0
    //      3. scan pages and invalidate(how?) those > actualSize
    throw new UnsupportedOperationException("Method not implemented yet");
  }

  @Override
  public boolean isDirty() {
    return dirtyPagesCount.get() > 0;
  }

  @Override
  public @NotNull Page pageByOffset(final long offsetInFile,
                                    final boolean forModification) throws IOException {
    final int pageIndex = toPageIndex(offsetInFile);
    return pageByIndex(pageIndex, forModification);
  }

  public Page pageByIndex(final int pageIndex,
                          final boolean forModification) throws IOException {
    if (readOnly && forModification) {
      throw new IOException("Read-only storage can't be modified");
    }

    if (pageIndex < 0) {
      throw new AssertionError("Page " + pageIndex + " must be >=0");
    }

    final long startedAtNs = pageCacheStatistics.startTimestampNs();

    for (int attempt = 0; ; attempt++) {
      if (isClosed()) {
        throw new ClosedStorageException("Storage is already closed: " + file);
      }
      final PageImpl page = pages.lookupOrCreate(
        pageIndex,
        this::createUninitializedPage
      );

      try {
        //busy-spin on: (check page is USABLE, and increment useCount)
        while (!page.tryAcquireForUse(this)) {
          if (page.isNotReadyYet()) { //page just created: try to load it
            if (page.tryPrepareForUse(this::loadPageData)) {
              continue;
            }
            //MAYBE RC: Page loading could take quite a while. What could we do to avoid busy-spinning
            //          on the page loading by another thread?
            //          One idea is to have something like (optional, lazy) CompletableFuture in Page,
            //          which is returned from .tryPrepareForUse() instead of 'false', and could be
            //          waited on (or used to suspend coroutine).
            //          Another approach: FPCache has a shared Condition instance, which it notifies at
            //          the end of each turn
          }
          Thread.yield();//MAYBE RC: Thread.onSpinWait(); (java9+)
        }
        pageCacheStatistics.pageRequested(page.pageSize(), startedAtNs);
        return page;
      }
      catch (IOException e) {
        if (attempt > MAX_ATTEMPTS_TO_ACQUIRE_PAGE) {
          throw new AssertionError(page + " can't be acquired in " + attempt + " attempts: either we're very unlucky, or it is a bug");
        }
        LOG.trace("Page " + page + " likely released -> request it again (" + e.getMessage() + ")");

        //Worst case scenario: page is in the middle of unmapping (>=ABOUT_TO_UNMAP). We can't
        // interrupt this process: page unmapping must be finalized, and then on subsequent
        // request page will be mapped back, anew.

        //But at least we could try to assist page reclamation:
        if (page.isAboutToUnmap()) {
          // This branch is not only an optimization: it prevents a pathological scenario (IDEA-323586)
          // there housekeeper thread skips its turns because there are already enough pages prepared to
          // reclaim, and because of that the particular page remains in ABOUT_TO_UNMAP forever, stalling
          // everybody who requesting it.
          final boolean succeed = page.tryMoveTowardsPreTombstone(/*entombYoung: */false);
          if (succeed) {
            pageCache.unmapPageAndReclaimBuffer(page);
          }
        }
        else {
          if (attempt < 10) {
            Thread.yield();
          }
          else {
            //instead of blind yield -- better to wait for housekeeper to make a turn
            pageCache.waitForHousekeepingTurn(1 /*ms*/);
          }
        }
      }
    }
  }

  @Override
  public boolean isClosed() {
    return closingInProgress != null;
  }

  @Override
  public void force() throws IOException {
    if (isDirty()) {
      pages.flushAll();
    }
  }

  /**
   * Synchronous close: i.e. tries to reclaim all the pages in a current thread, and waits until {@link FilePageCacheLockFree}
   * cleans up everything associated with this storage. After this method terminates, it is safe to try to create
   * new storage from the same file.
   */
  @Override
  public void close() throws IOException {
    if (isClosed()) {
      return;
    }
    //RC: let's do here as much of closing as we could: scan all pages and move all USABLE to
    //    ABOUT_TO_UNMAP, etc. By that we lower pressure on the housekeeper thread -- it will have
    //    some work to do anyway, but much less. This seems to be a good tradeoff, since thread
    //    invoking .close() usually not in a hurry -- and if it does hurry, it could use .closeAsync()
    pageCache.tryToReclaimAll(pages);

    final Future<?> future = closeAsync();
    try {//MAYBE pageCache.wakeupHousekeeper()?
      future.get();
    }
    catch (ExecutionException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        throw (IOException)cause;
      }
      else {
        throw new IOException("Can't close storage for " + file, cause);
      }
    }
    catch (InterruptedException e) {
      //RC: storage.close() method throwing InterruptedException is a lot of pain, because in many places only IO
      //    exceptions are expected, and there is no better way to deal with IE other then rethrow it as some kind
      //    of IO exception. So finally I decided to do it right here, instead of repeating it in 10s places around
      //    the codebase:
      final InterruptedIOException ioEx = new InterruptedIOException("Closing storage for " + file + " was interrupted");
      ioEx.addSuppressed(e);
      throw ioEx;
    }
  }

  /**
   * Enqueues storage backing structures cleanup, but do not wait for completion.
   * Storage {@link #isClosed()}=true after this method, but an attempt to open new storage
   * from the same file may fail, since {@link FilePageCacheLockFree} may not yet cleans up
   * everything related to the current storage.
   */
  public synchronized Future<?> closeAsync() {
    if (!isClosed()) {
      final CompletableFuture<Object> closingProgress = new CompletableFuture<>();
      closingInProgress = closingProgress;
      pageCache.enqueueStoragePagesClosing(this, closingProgress);
    }
    return closingInProgress;
  }

  public void closeAndRemoveAllFiles() throws IOException, InterruptedException {
    if (!isClosed()) {
      close();
    }

    FileUtil.delete(file);
  }


  @Override
  public int toOffsetInPage(final long offsetInFile) {
    return (int)(offsetInFile % pageSize);
  }

  @Override
  public String toString() {
    return "PagedFileStorage[" + file + "]" +
           "{size: " + actualSize.get() + ", dirtyPages: " + dirtyPagesCount.get() + "}" +
           "{pageSize: " + pageSize + ", "
           + (isClosed() ? "closed " : "")
           + (isReadOnly() ? "readOnly " : "")
           + (isNativeBytesOrder() ? "nativeByteOrder" : "")
           + "}";
  }

  /* ==================== infrastructure: ============================================================ */

  private int toPageIndex(final long offsetInFile) {
    final int pageIndex = (int)(offsetInFile / pageSize);
    if (pageIndex < 0) {
      throw new AssertionError("pageIndex(offset: " + offsetInFile + ") = " + pageIndex + ", but must be >=0");
    }
    return pageIndex;
  }

  /** @return true if value is fully located on a single page */
  private boolean isValueAlignedToPage(final long offsetInFile,
                                       final int valueLength) {
    final int offsetInPage = (int)(offsetInFile % pageSize);
    final int remainsOnPage = pageSize - offsetInPage;
    return valueLength <= remainsOnPage;
  }

  private void checkValueIsPageAligned(final long offsetInFile,
                                       final int valueLength) throws IOException {
    if (!isValueAlignedToPage(offsetInFile, valueLength)) {
      throw new IOException(
        valueLength + " bytes @ " + offsetInFile + " break page [" + pageSize + "b] border: " +
        "use PagedStorageWithUnalignedAccess wrapper if un-aligned primitive access is needed"
      );
    }
  }

  PagesTable pages() {
    return pages;
  }

  <R> R executeOp(final @NotNull FileChannelOperation<R> operation,
                  final boolean readOnly) throws IOException {
    return storageLockContext.executeOp(file, operation, readOnly);
  }

  <R> R executeIdempotentOp(final @NotNull FileChannelIdempotentOperation<R> operation,
                            final boolean readOnly) throws IOException {
    return storageLockContext.executeIdempotentOp(file, operation, readOnly);
  }

  private PageImpl createUninitializedPage(final int pageIndex) {
    final ReentrantReadWriteLock lockToUseWithPage = pageContentLockingStrategy.lockForPage(this, pageIndex);
    return RWLockProtectedPageImpl.createBlankWithExplicitLock(pageIndex, pageSize, pageToStorageHandle, lockToUseWithPage);
  }

  private ByteBuffer loadPageData(final @NotNull PageImpl pageToLoad) throws IOException {
    if (isClosed()) {
      throw new ClosedStorageException("Storage is already closed");
    }
    if (!pageToLoad.isLoading()) {
      throw new AssertionError("Bug: page must be in LOADING, but " + pageToLoad);
    }
    final long startedAtNs = pageCacheStatistics.startTimestampNs();
    final ByteBuffer pageBuffer = pageCache.allocatePageBuffer(pageSize);
    pageBuffer.order(nativeBytesOrder ? ByteOrder.nativeOrder() : BIG_ENDIAN);
    try {
      executeIdempotentOp(ch -> {
        final int readBytes = ch.read(pageBuffer, pageToLoad.offsetInFile());
        final int bytesActuallyRead = Math.max(0, readBytes);
        if (readBytes < pageSize) {
          fillWithZeroes(pageBuffer, bytesActuallyRead, pageSize);
        }
        pageCacheStatistics.pageRead(bytesActuallyRead, startedAtNs);
        return pageBuffer;
      }, isReadOnly());
    }
    catch (Throwable t) {
      pageCache.reclaimPageBuffer(pageSize, pageBuffer);
      throw t;
    }
    return pageBuffer;
  }

  private void flushPage(final @NotNull ByteBuffer bufferToSave,
                         final long offsetInFile) throws IOException {
    final long startedAtNs = pageCacheStatistics.startTimestampNs();
    final int bytesToStore = bufferToSave.remaining();
    executeIdempotentOp(ch -> {
      ch.write(bufferToSave, offsetInFile);
      return null;
    }, isReadOnly());

    pageCacheStatistics.pageWritten(bytesToStore, startedAtNs);
  }

  private static final int MAX_FILLER_SIZE = 8192;
  /** Seems like the fastest method to fill ByteBuffer with zeroes is to copy an array of zeroes into it */
  private static final byte[] ZEROES = new byte[MAX_FILLER_SIZE];

  private static void fillWithZeroes(final @NotNull ByteBuffer pageBuffer,
                                     final int startOffsetInclusive,
                                     final int endOffsetExclusive) {
    int currentOffset = startOffsetInclusive;
    while (currentOffset < endOffsetExclusive) {
      final int remains = endOffsetExclusive - currentOffset;
      final int toFillNow = Math.min(remains, MAX_FILLER_SIZE);
      pageBuffer.put(ZEROES, 0, toFillNow);
      currentOffset += toFillNow;
    }
  }

  private static @NotNull StorageLockContext findOutAppropriateContext(final @Nullable StorageLockContext storageLockContext) {
    final StorageLockContext threadContext = THREAD_LOCAL_STORAGE_LOCK_CONTEXT.get();
    if (threadContext != null) {
      if (storageLockContext != null && storageLockContext != threadContext) {
        throw new IllegalStateException("Context(" + storageLockContext + ") != THREAD_LOCAL_STORAGE_LOCK_CONTEXT(" + threadContext + ")");
      }
      return threadContext;
    }

    return storageLockContext != null ? storageLockContext : DEFAULT_LOCK_CONTEXT;
  }
}
