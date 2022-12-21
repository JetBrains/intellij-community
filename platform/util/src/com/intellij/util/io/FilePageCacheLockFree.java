// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IntRef;
import com.intellij.openapi.util.ThrowableNotNullFunction;
import com.intellij.util.io.pagecache.FrugalQuantileEstimator;
import com.intellij.util.io.pagecache.PageContentLoader;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Flushable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.IntFunction;

import static java.util.Comparator.comparing;

/**
 * Maintains 'pages' of data (in the form of {@linkplain Page}), from file storages {@linkplain PagedFileStorageLockFree}.
 * <br>
 * This class is not a file cache per se (this is {@linkplain PagesTable}) but mostly a cache housekeeper:
 * it does background jobs to keep pages cache at bay. I.e. it maintains a pool of buffers, limits the
 * total size of buffers in use, does async flushing, flushes and re-uses the least used pages, and so on.
 * <br>
 * Page cache maintains limit on number of page cached: {@linkplain #cacheCapacityBytes}. As the limit is
 * reached, oldest and/or least used pages are evicted from cache (and flushed to disk). Pages also evicted
 * with their owner {@linkplain PagedFileStorageLockFree} re-registration {@linkplain #enqueueStoragePagesClosing(PagedFileStorageLockFree, CompletableFuture)}
 * <p>
 */
@ApiStatus.Internal
public final class FilePageCacheLockFree implements AutoCloseable {
  private static final Logger LOG = Logger.getInstance(FilePageCacheLockFree.class);

  /** Initial size of page table hashmap in {@linkplain PagesTable} */
  private static final int INITIAL_PAGES_TABLE_SIZE = 1 << 5;

  /**
   * 'Tokens of usefulness' are added to the page each time page is acquired for use, and also
   * each time housekeeper thread finds page in use (per-use)
   */
  private static final int TOKENS_PER_USE = 8;
  /** Tokens initially assigned to the new page */
  private static final int TOKENS_INITIALLY = 2 * TOKENS_PER_USE;

  /** Housekeeper thread collects ~10% of the least useful pages for reclaim */
  private static final int DEFAULT_PERCENTS_OF_PAGES_TO_PREPARE_FOR_RECLAIM = 10;


  /* ================= instance fields ============================================================== */

  private final long cacheCapacityBytes;

  /** Total bytes cached as DirectByteBuffers */
  private final AtomicLong totalNativeBytesCached = new AtomicLong(0);
  /** Total bytes cached as HeapByteBuffers */
  private final AtomicLong totalHeapBytesCached = new AtomicLong(0);

  //@GuardedBy("pagesPerStorage")
  private final Object2ObjectOpenHashMap<Path, PagesTable> pagesPerFile = new Object2ObjectOpenHashMap<>();

  /**
   * Queue of pages that are likely the best suited to reclaim -- i.e. unmap, and re-use their
   * buffer for another page needs to be loaded. This queue is filled with background thread
   * periodically scanning all pages, and selecting the pages not used right now, and the least
   * used recently.
   * Bear in mind that page state changes async, so this queue is not exactly up-to-date.
   * I.e. pages in this queue are just 'likely' to be the ones ready to reclaim -- it is
   * possible that somebody starts using a page after it was put in the queue, and hence the
   * page in queue becomes not eligible to reclaim anymore.
   */
  private volatile ConcurrentLinkedQueue<Page> pagesToProbablyReclaimQueue = new ConcurrentLinkedQueue<>();

  private final ConcurrentLinkedQueue<Command> commandsQueue = new ConcurrentLinkedQueue<>();

  private final PagesForReclaimCollector pagesForReclaimCollector;

  private final Thread housekeeperThread;

  /** TODO unfinished work: PageCache lifecycle state */
  private volatile int state = 0;


  protected FilePageCacheLockFree(final long cacheCapacityBytes) {
    this(cacheCapacityBytes, r -> new Thread(r, "FilePageCache housekeeper"));
  }

  protected FilePageCacheLockFree(final long cacheCapacityBytes,
                                  final ThreadFactory maintenanceThreadFactory) {
    if (cacheCapacityBytes <= 0) {
      throw new IllegalArgumentException("Capacity(=" + cacheCapacityBytes + ") must be >0");
    }
    this.cacheCapacityBytes = cacheCapacityBytes;

    this.pagesForReclaimCollector = new PagesForReclaimCollector(
      DEFAULT_PERCENTS_OF_PAGES_TO_PREPARE_FOR_RECLAIM,
      2 * DEFAULT_PERCENTS_OF_PAGES_TO_PREPARE_FOR_RECLAIM
    );

    housekeeperThread = maintenanceThreadFactory.newThread(this::cacheMaintenanceLoop);
    housekeeperThread.setDaemon(true);
  }

  public long getCacheCapacityBytes() {
    return cacheCapacityBytes;
  }

  /**
   * Method registers storage in a file page cache, and returns PageTable, maintained by this cache.
   * There is no paired 'unregister' method -- instead there is {@link #enqueueStoragePagesClosing(PagedFileStorageLockFree, CompletableFuture)}
   * method which storage should call to ask page cache to do a cleanup as a part of maintenance
   * and housekeeping.
   */
  public PagesTable registerStorage(final @NotNull PagedFileStorageLockFree storage) throws IOException {
    synchronized (pagesPerFile) {
      final Path absolutePath = storage.getFile().toAbsolutePath();
      if (pagesPerFile.containsKey(absolutePath)) {
        throw new IOException("Storage for [" + absolutePath + "] is already registered");
      }

      final boolean firstStorageRegistered = pagesPerFile.isEmpty();

      final PagesTable pages = new PagesTable(INITIAL_PAGES_TABLE_SIZE);
      pagesPerFile.put(absolutePath, pages);

      if (firstStorageRegistered && state == 0) {
        //don't start housekeeper thread before actual storage is registered: there are a lot of
        // FPCaches created 'just in case', and not actually used.
        housekeeperThread.start();
        state = 1;
        //TODO RC: need .closed field?
      }
      return pages;
    }
  }

  protected Future<?> enqueueStoragePagesClosing(final @NotNull PagedFileStorageLockFree storage,
                                                 final @NotNull CompletableFuture<Object> finish) {
    final CloseStorageCommand task = new CloseStorageCommand(storage, finish);
    commandsQueue.add(task);
    return task.onFinish;
  }

  @Override
  public void close() throws InterruptedException {
    housekeeperThread.interrupt();
    housekeeperThread.join();
  }

  private void cacheMaintenanceLoop() {
    while (!Thread.interrupted()) {
      try {
        final int pagesPreparedToReclaim = pagesForReclaimCollector.totalPagesPreparedToReclaim();
        final int pagesRemainedToReclaim = pagesToProbablyReclaimQueue.size();

        //Are there a lot of pages for reclaim? Or commands to process?
        if (pagesRemainedToReclaim <= pagesPreparedToReclaim / 2
            || !commandsQueue.isEmpty()) {

          doCacheMaintenanceTurn();
        }

        //assess allocation pressure and adjust our efforts
        if (pagesRemainedToReclaim > pagesPreparedToReclaim / 2) {
          //allocation pressure low: could collect less and sleep more
          pagesForReclaimCollector.decreasePercentage();
          Thread.sleep(1);
        }
        else if (pagesRemainedToReclaim > 0) {
          Thread.yield();//allocation pressure high: just yield
        }
        else {
          //allocation pressure is so high we don't catch up with it:
          // no time to wait
          // need to collect more
          pagesForReclaimCollector.increasePercentage();
        }
      }
      catch (InterruptedException e) {
        return;
      }
      catch (Throwable t) {
        LOG.error("Exception in FilePageCache housekeeper thread (thread continue to run)", t);
      }
    }
    LOG.info("maintenance loop interrupted -> exiting");
  }

  //TODO RC:
  //     1. Trace allocation pressure (pagesToReclaim - pagesToReclaimQueue.size), and use
  //        QuantilesEstimator(80-90%) to estimate percentage of pages to prepare to reclaim.
  //        Also use it to estimate how much 'eager flushes' needs to be done.
  //     2. In .allocatePageBuffer(): trace counts of .flush() invoked, and heap buffers allocated
  //     5. If a lot of .flush() invoked -> increase probability of 'eager flushes' in (2)
  //     6. How to use 'heap buffers count' and 'capacity overflow'?


  //================================================================================================
  // IDEA: Find out current 'page reclamation pressure' -- i.e. if there are a lot of pages still
  // available for allocation -> no need to release anything, except for STATE_TO_UNMAP (because
  // apt PagedFileStorage was closed). Also we could .force() pages which are not used for a long
  // (hence low chance of contention .force() vs use)
  // As long, as page pressure becomes higher -> we start to be more aggressive in reclaiming pages


  //================================================================================================
  //Main idea is following: all else equal, the page already loaded is always worth more than not
  // loaded. It means that we never unload a page without a reason -- even if the page wasn't used
  // for long. There are 2 reasons for unloading a page: either PagedStorage is closed, or a new page
  // needs to be allocated. But we don't want to look up for page to reclaim through all the cached
  // pages -- this would make new page caching awfully slow. Instead, we scan through the pages
  // regularly, and maintain the list of _candidates_ to reclamation -- i.e. pages, that have the
  // lowest utility, and are not used right now. We eagerly initiate flush for such pages, so nothing
  // would prevent such a page from being unloaded and reclaimed as the need arises.

  private void doCacheMaintenanceTurn() {
    //Closed storages are the easiest way to reclaim pages -- eat that low-hanging fruit first:
    reclaimClosedStorages(/* max per turn: */ 1);


    //Now scan all pages and collect candidates for reclamation: basically, build not-fully-up-to-date
    // index for query like
    // `SELECT top <N> FROM pages WHERE usageCount=0 ORDER BY utility DESC`

    final Map<Path, PagesTable> pagesPerStorage = threadSafeCopyOfPagesPerStorage();
    int totalPagesAlive = 0;
    pagesForReclaimCollector.startCollectingTurn();
    final Collection<PagesTable> pagesTables = pagesPerStorage.values();
    for (PagesTable pagesTable : pagesTables) {
      final AtomicReferenceArray<Page> pages = pagesTable.pages;
      int pagesAliveInTable = 0;
      for (int i = 0; i < pages.length(); i++) {
        final Page page = pages.get(i);
        if (page == null || page.isTombstone()) {
          continue;
        }

        pagesAliveInTable++;
        totalPagesAlive++;

        if (page.isAboutToUnmap() && page.usageCount() == 0) {
          //reclaim page right now, no need to enqueue it for later:
          if (page.tryMoveTowardsTomb(false) == Page.STATE_TOMBSTONE) {
            unmapPageAndReclaimBuffer(page);
            continue;
          }
        }

        final int pageUsefulness = adjustPageUsefulness(page);
        page.tokensOfUsefulnessLocal = pageUsefulness;

        pagesForReclaimCollector.checkPageGoodForReclaim(page);
      }

      //shrink table if too many tombstones:
      pagesTable.shrinkIfNeeded(pagesAliveInTable);
    }

    pagesForReclaimCollector.finishCollectingTurn();

    pagesToProbablyReclaimQueue = pagesForReclaimCollector.pagesForReclaimAsQueue();

    //if <50% of pages collected to reclaim are clean -> try to eagerly flush some dirty pages:
    pagesForReclaimCollector.ensureEnoughCleanPagesToReclaim(0.5);
  }

  private static int adjustPageUsefulness(final @NotNull Page page) {
    final int usageCount = page.usageCount();
    if (usageCount > 0) {
      return page.addTokensOfUsefulness(usageCount * TOKENS_PER_USE);
    }
    else {//exponential decay of usefulness:
      return page.decayTokensOfUsefulness(TOKENS_PER_USE - 1, TOKENS_PER_USE);
    }
  }

  @NotNull
  private List<PagedFileStorageLockFree> reclaimClosedStorages(final int maxStoragesToProcess) {
    final List<PagedFileStorageLockFree> successfullyReclaimed = new ArrayList<>();
    for (int i = 0; i < maxStoragesToProcess; i++) {
      final Command command = commandsQueue.poll();
      if (command == null) {
        break;
      }

      if (command instanceof CloseStorageCommand) {
        final CloseStorageCommand closeStorageCommand = (CloseStorageCommand)command;
        final PagedFileStorageLockFree storage = closeStorageCommand.storageToClose;
        final CompletableFuture<?> futureToFinalize = closeStorageCommand.onFinish;
        if (!storage.isClosed()) {
          final AssertionError error =
            new AssertionError("Code bug: storage " + storage + " must be closed before CloseStorageCommand is queued");
          futureToFinalize.completeExceptionally(error);
          throw error;
        }

        final PagesTable pagesTable = storage.pages();
        // RC: Actually, we don't need to _reclaim_ pages of .close()-ed storage -- we need to flush
        //     them, but the pages themselves are owned by cache, not storage. So it may be
        //     reasonable to just leave pages there they are, until they are either reclaimed by
        //     a regular reclamation path (because their utility is completely decayed under no
        //     use), or re-used by a new storage opened for the same file. IMHO, for our use-cases,
        //     the ability to re-use pages for re-opened storages is not so much important, but the
        //     opportunity to simplify .close() codepath may be worth it.

        //TODO RC: catch UncheckedIOException from page.flush(), and collect them.
        //         Do limited retries: count unsuccessful page.flush(), and give up after
        //         N tries -- remove task from queue, rise an exception leading to app restart
        //         request (not worth to allow user continue working if we know work can't be
        //         saved)
        final boolean somePagesStillInUse = tryToReclaimAll(pagesTable);
        if (!somePagesStillInUse) {
          final Path file = storage.getFile();
          try {
            PageCacheUtils.CHANNELS_CACHE.closeChannel(file);
          }
          catch (IOException e) {
            LOG.error("Can't close channel for " + file, e);
            futureToFinalize.completeExceptionally(e);
          }
          successfullyReclaimed.add(storage);
          synchronized (pagesPerFile) {
            final Path absolutePath = storage.getFile().toAbsolutePath();
            final PagesTable removed = pagesPerFile.remove(absolutePath);
            assert removed != null : "Storage for [" + absolutePath + "] must exists";
          }
          futureToFinalize.complete(null);
        }
        else {
          //return command to the queue, to be re-tried later:
          commandsQueue.offer(command);
        }
      }
    }

    return successfullyReclaimed;
  }

  @NotNull
  private Map<Path, PagesTable> threadSafeCopyOfPagesPerStorage() {
    synchronized (pagesPerFile) {
      return new HashMap<>(pagesPerFile);
    }
  }


  /**
   * Method tries to unmap and reclaim all pages of a given PagesTable. It moves all pages
   * to {@link Page#STATE_ABOUT_TO_UNMAP} state, and if page has usageCount=0 -> reclaim it immediately.
   * Pages with usageCount > 0 are not reclaimed, and method returns false if there is at least one
   * such a page. Method is designed to be called repeatedly, until all pages are reclaimed.
   */
  protected boolean tryToReclaimAll(final @NotNull PagesTable pagesTable) {
    final AtomicReferenceArray<Page> pages = pagesTable.pages;
    boolean somePagesStillInUse = false;
    for (int i = 0; i < pages.length(); i++) {
      final Page page = pages.get(i);
      if (page == null || page.isTombstone()) {
        continue;
      }

      if (!page.isNotReadyYet()) {
        //Simple cases: USABLE or ABOUT_TO_UNMAP
        final int finalState = page.tryMoveTowardsTomb(/*entombYoung: */false);
        if (finalState == Page.STATE_TOMBSTONE) {
          unmapPageAndReclaimBuffer(page);
        }
      }
      else {
        //More tangled cases: Page could be
        // 1) NOT_READY_YET (=> usageCount=0)
        // 2) was NOT_READY_YET just before but is already promoted to USABLE

        //MAYBE RC: Really, this branch is just an optimization, a try to release pages a bit faster.
        //          If we remove this branch, NOT_READY_YET pages will be naturally promoted to USABLE,
        //          and next .tryToReclaimAll() call moves them to ABOUT_TO_RECLAIM and eventually
        //          TOMBSTONE by the branch above alone. So the branch above (!isNotReadyYet) is the
        //          crucial one, while this branch just tries to avoid (short-circuit) unnecessary
        //          allocation-loading-reclaiming of the pages that are initialized concurrently with
        //          .close().
        //          Now, except for tests & benchmarks, I doubt there are many cases there a lot of
        //          pages initialized concurrently with .close() -- so, it seems, this branch provides
        //          little benefit for its complexity.
        //          There is a reason, though, why I leave this branch here: it also adds 'robustness'.
        //          I.e. if page acquisition code _fails_ and leaves the page in NOT_READY_YET state --
        //          this branch _could_ reclaim such a page, while without it the page remains leaking.
        //          Maybe I'll reconsider this in the future.

        //Acquire page.writeLock to stop page from being promoted to USABLE (if it hasn't been yet):
        if (page.pageLock.writeLock().tryLock()) {
          try {
            final int finalState = page.tryMoveTowardsTomb(/*entombYoung: */ true);
            if (finalState == Page.STATE_TOMBSTONE) {
              //If we just entomb NOT_READY_YET page => it has .data=null
              //   but page could be already promoted to USABLE, in which case .data != null, and
              //   should be reclaimed:
              if (page.data != null) {
                unmapPageAndReclaimBuffer(page);
              }
            }
          }
          finally {
            page.pageLock.writeLock().unlock();
          }
        }
        else {
          //We've lost the race: somebody locked the page for read/write, hence page (likely)
          // was promoted to USABLE and acquired (usageCount>0).
          // => try at least to mark the page as ABOUT_TO_UNMAP, to stop additional clients
          // => wait till next time, hope the page will be released then
          //MAYBE RC: seems like this is neat-peeking -- case is rare, and process it here adds almost
          //          nothing, but complicates code. Better leave such pages until next turn?
          final int finalState = page.tryMoveTowardsTomb(/*entombYoung: */ false);
          if (finalState == Page.STATE_TOMBSTONE) {
            unmapPageAndReclaimBuffer(page);
          }
        }
      }

      //if !TOMBSTONE => wait till next time, hope the page will be released eventually
      somePagesStillInUse = !page.isTombstone();
    }
    return somePagesStillInUse;
  }

  /**
   * Reclaims page: flushes page content if needed, and release page buffer. Page must
   * be in state=TOMBSTONE
   */
  private void unmapPageAndReclaimBuffer(final @NotNull Page candidateToReclaim) {
    if (!candidateToReclaim.isTombstone()) {
      throw new AssertionError("Bug: page must be TOMBSTONE: " + candidateToReclaim);
    }
    if (candidateToReclaim.isDirty()) {
      //MAYBE RC: flush() could be off-loaded to IO thread pool, instead of slowing down housekeeper
      //         thread
      try {
        candidateToReclaim.flush();
      }
      catch (IOException e) {
        throw new UncheckedIOException("Can't flush page: " + candidateToReclaim, e);
      }
    }

    final ByteBuffer data = candidateToReclaim.data;
    if (data.isDirect()) {
      DirectByteBufferAllocator.ALLOCATOR.release(data);
      totalNativeBytesCached.addAndGet(-data.capacity());
    }
    else {
      totalHeapBytesCached.addAndGet(-data.capacity());
    }
    candidateToReclaim.data = null;
  }

  protected ByteBuffer allocatePageBuffer(final int bufferSize) {
    int dirtyPagesSkipped = 0;
    while (totalNativeBytesCached.get() > cacheCapacityBytes - bufferSize) {
      final Page candidateToReclaim = pagesToProbablyReclaimQueue.poll();
      if (candidateToReclaim != null) {

        if (candidateToReclaim.usageCount() > 0) {
          continue;//page is in use by somebody
        }
        if (candidateToReclaim.isDirty()) {
          //RC: we _prefer_ to not reclaim dirty pages here, on page allocation path, since .flush()
          //    creates unpredictable delays -- hence we _prefer_ to skip dirty pages, looking for
          //    non-dirty ones.
          //    Unfortunately, we can't skip dirty pages altogether, since it could be there are
          //    _no_ non-dirty pages available -- and scanning all pagesToProbablyReclaim to find it
          //    out is also a high cost. Hence, we skip dirty pages probabilistically: we accept
          //    Nth dirty page with probability N/10:
          final int rnd10 = ThreadLocalRandom.current().nextInt(10);
          if (dirtyPagesSkipped <= rnd10) {
            dirtyPagesSkipped++;
            pagesToProbablyReclaimQueue.offer(candidateToReclaim);
            //MAYBE RC: also queue async flush for the page?
            continue;
          }
        }

        final int finalState = candidateToReclaim.tryMoveTowardsTomb(/*entombYoung: */false);
        if (finalState == Page.STATE_TOMBSTONE) {
          // > 1 thread could try to reclaim the page, but we win the race:
          unmapPageAndReclaimBuffer(candidateToReclaim);
        }

        //if (candidateToReclaim.usageCount() == 0) {//re-check again
        //  if (candidateToReclaim.isUsable()) {
        //    candidateToReclaim.markAsAboutToUnmap();
        //  }
        //  //MAYBE RC: there are a lot of repetitive checks here, better find way to clean them up.
        //  //          Looks like the right solution could be to have _try_UnmapAndReclaim(page)
        //  //          method, which uses CAS to ensure state transfer atomicity, and allows calls
        //  //          from non-applicable states -- just return false, not throw exception.
        //  if (candidateToReclaim.isAboutToUnmap() &&
        //      candidateToReclaim.usageCount() == 0 &&
        //      !candidateToReclaim.isTombstone()) {
        //    try {
        //      unmapAndReclaimPage(candidateToReclaim);
        //    }
        //    catch (UncheckedIOException e) {
        //      LOG.warn("Can't flush page " + candidateToReclaim, e);
        //    }
        //  }
        //}
      }
      else {
        //RC: instead of forcing existing pages to unload -- allocate heap buffers.
        //    And after that, get them out ASAP during regular background scans?
        totalHeapBytesCached.addAndGet(bufferSize);
        return ByteBuffer.allocate(bufferSize);
      }
    }
    final ByteBuffer buffer = DirectByteBufferAllocator.ALLOCATOR.allocate(bufferSize);
    totalNativeBytesCached.addAndGet(buffer.capacity());
    return buffer;
  }

  /**
   * Open-addressing hashmap specialized for {@link Page} objects. PagesTable is owned by
   * {@link PagedFileStorageLockFree}, but FilePageCache require a lot of access to its internals,
   * hence class is located here.
   */
  protected static class PagesTable {
    private static final double GROWTH_FACTOR = 1.5;
    private static final double SHRINK_FACTOR = 2;
    private static final int MIN_TABLE_SIZE = 16;

    private final float loadFactor;

    /** Pages in pages array: count all !null entries, including tombstones */
    //@GuardedBy(pagesLock.writeLock)
    private int pagesCount = 0;

    /**
     * Content: Page{state: NOT_READY | READY_FOR_USE | TO_UNMAP}.
     * Reads are non-blocking, writes must be guarded by .pagesLock
     */
    @NotNull
    private volatile AtomicReferenceArray<Page> pages;

    private transient final ReentrantReadWriteLock pagesLock = new ReentrantReadWriteLock();


    protected PagesTable(final int initialSize) {
      this(initialSize, 0.5f);
    }

    protected PagesTable(final int initialSize,
                         final float loadFactor) {
      this.loadFactor = loadFactor;
      final int size = Math.max(MIN_TABLE_SIZE, (int)(initialSize / this.loadFactor));
      pages = new AtomicReferenceArray<>(size);
    }

    @Nullable
    public Page lookupIfExist(final int pageIndex) {
      return findPageOrInsertionIndex(this.pages, pageIndex, /*insertionIndexRef: */ null);
    }

    @NotNull
    public Page lookupOrCreate(final int pageIndex,
                               final @NotNull IntFunction<Page> uninitializedPageFactory,
                               final @NotNull PageContentLoader pageContentLoader) throws IOException {
      final Page page = findPageOrInsertionIndex(this.pages, pageIndex, /*insertionIndexRef: */null);
      if (page != null) {
        return page;
      }
      return insertNewPage(pageIndex, uninitializedPageFactory, pageContentLoader);
    }

    public void flushAll() throws IOException {
      //MAYBE RC: do we really need readLock here? Seems like we could skip the lock, but it
      //          is hard to define semantics of that .flush() implementation -- i.e. new pages could
      //          be created and inserted in parallel, and those pages will or will not be flushed, depending
      //          on there in .pages array they are inserted, and there iteration index is at the moment of
      //          insertion.
      //          ...But really even with readLock we have the same issue: some of already existent pages
      //          could be modified in parallel, and such a modifications will or will not be flushed depending
      //          on the location of the modified pages, and current iteration index.
      pagesLock.readLock().lock();
      try {
        for (int i = 0; i < pages.length(); i++) {
          final Page page = pages.get(i);
          if (page != null && page.isDirty()) {
            page.flush();
          }
        }
      }
      finally {
        pagesLock.readLock().unlock();
      }
    }

    @NotNull
    private Page insertNewPage(final int pageIndex,
                               final IntFunction<Page> uninitializedPageFactory,
                               final PageContentLoader pageContentLoader) throws IOException {

      //Don't try to be lock-free on updates, just avoid holding the _global_ lock during IO:
      // 1) put blankPage under the pagesLock,
      // 2) release the pagesLock,
      // 3) load page outside the pagesLock, under per-page lock
      final IntRef insertionIndexRef = new IntRef();
      final Page blankPage;
      pagesLock.writeLock().lock();
      try {
        final Page alreadyInsertedPage = findPageOrInsertionIndex(this.pages, pageIndex, insertionIndexRef);
        if (alreadyInsertedPage != null) {
          //race: somebody inserted the page between lock-free speculative check and locked re-check
          return alreadyInsertedPage;
        }

        blankPage = uninitializedPageFactory.apply(pageIndex);

        final int insertionIndex = insertionIndexRef.get();
        if (insertionIndex >= 0) {
          pages.set(insertionIndex, blankPage);
          pagesCount++;

          if (pagesCount > pages.length() * loadFactor) {
            final int newTableSize = (int)((pagesCount / loadFactor) * GROWTH_FACTOR);
            rehashToSize(newTableSize);

            //FIXME RC: we need not only enlargement, but also shrinking! PageTable grows big temporary,
            //         while we read the file intensively -- but after that period table should shrinks.
            //         Contrary to the enlargement, shrinking is better to be implemented by maintenance
            //         thread, in background. This is because a lot of entries in enlarged table would be
            //         a tombstones (pages already reclaimed, but entries remain) and keep count of those
            //         tombstones is not convenient -- Page status change is generally detached PageTable
            //         logic, and better to keep that way. But maintenance thread scans all the pages anyway,
            //         so it could easily count (tombstones vs alive) entries, and trigger shrinking if there
            //         are <30-40% entries are alive.
          }
        }
        else {
          // (page == null && insertionIndex < 0) -> no space remains in table
          // But it is unexpected, since we resize table well in advance
          throw new AssertionError("Bug: table[len:" + pages.length() + "] is full, but only " + pagesCount + " entries are in." + pages);
        }
      }
      finally {
        pagesLock.writeLock().unlock();
      }

      blankPage.pageLock.writeLock().lock();
      try {
        if (blankPage.isTombstone()) {
          throw new ClosedStorageException("Storage is already closed");
        }
        if (!blankPage.isNotReadyYet()) {
          throw new AssertionError("Page must be {NOT_READY_YET, TOMBSTONE}, but " + blankPage);
        }
        blankPage.prepareForUse(pageContentLoader);
        if (blankPage.isNotReadyYet()) {
          //RC: Page state could be any of {USABLE, ABOUT_TO_UNMAP, TOMBSTONE} here. USABLE is the
          //    obvious case, but {ABOUT_TO_UNMAP, TOMBSTONE} could happen if new page allocation
          //    races with storage closing: in such scenario page could be entombed async immediately
          //    after .prepareForUse() made it USABLE -- nothing prevents it, since page has
          //    usageCount=0 still.
          throw new AssertionError("Page must be {USABLE, ABOUT_TO_UNMAP, TOMBSTONE}, but " + blankPage);
        }
        if (blankPage.isDirty()) {
          throw new AssertionError("Page must NOT be dirty just after .pageLoader: " + blankPage);
        }
        return blankPage;
      }
      finally {
        blankPage.pageLock.writeLock().unlock();
      }
    }

    private void rehashToSize(final int newPagesSize) {
      assert pagesLock.writeLock().isHeldByCurrentThread() : "Must hold writeLock while rehashing";

      final AtomicReferenceArray<Page> newPages = new AtomicReferenceArray<>(newPagesSize);
      final int pagesCopied = rehashWithoutTombstones(pages, newPages);

      this.pagesCount = pagesCopied;
      this.pages = newPages;
    }

    /** Shrink table if alivePagesCount is too small for current size. */
    protected boolean shrinkIfNeeded(final int alivePagesCount) {
      final int expectedTableSize = (int)(alivePagesCount / loadFactor);
      if (expectedTableSize >= MIN_TABLE_SIZE
          && expectedTableSize * SHRINK_FACTOR < pages.length()) {
        pagesLock.writeLock().lock();
        try {
          rehashToSize(expectedTableSize);
          return true;
        }
        finally {
          pagesLock.writeLock().unlock();
        }
      }
      return false;
    }

    /**
     * Method hashes pages from sourcePages into targetPages, using {@link #findPageOrInsertionIndex(AtomicReferenceArray, int, IntRef)}.
     * Pages with [state:TOMBSTONE] are skipped during the copy.
     *
     * @return number of pages copied
     */
    private static int rehashWithoutTombstones(final @NotNull AtomicReferenceArray<Page> sourcePages,
                                               final @NotNull AtomicReferenceArray<Page> targetPages) {
      final IntRef insertionIndexRef = new IntRef();
      int pagesCopied = 0;
      for (int i = 0; i < sourcePages.length(); i++) {
        final Page page = sourcePages.get(i);
        if (page != null && !page.isTombstone()) {
          final int pageIndex = page.pageIndex;
          final Page pageMustNotBeFound = findPageOrInsertionIndex(targetPages, pageIndex, insertionIndexRef);
          final int insertionIndex = insertionIndexRef.get();

          if (pageMustNotBeFound != null) {
            throw new AssertionError("Page[#" + pageIndex + "] copying now can't be already in .newPages! " + targetPages);
          }
          if (insertionIndex < 0) {
            //either targetPages is too small to fit, or code bug in hashing logic
            throw new AssertionError("Insertion index must be found for Page[#" + pageIndex + "] during rehash. " + targetPages);
          }

          targetPages.set(insertionIndex, page);
          pagesCopied++;
        }
      }
      return pagesCopied;
    }

    @Nullable
    private static Page findPageOrInsertionIndex(final @NotNull AtomicReferenceArray<Page> pages,
                                                 final int pageIndex,
                                                 final @Nullable IntRef insertionIndexRef) {
      final int length = pages.length();
      final int index = pageIndex % length;
      final int probeStep = probeStep(length);

      int firstTombstoneIndex = -1;
      for (int probeIndex = index, probeNo = 0;
           probeNo < length;
           probeNo++) {
        final Page page = pages.get(probeIndex);

        if (page == null) {
          //end of the probing sequence reached: no such page
          if (insertionIndexRef != null) {
            final int insertionIndex = firstTombstoneIndex >= 0 ? firstTombstoneIndex : probeIndex;
            insertionIndexRef.set(insertionIndex);
          }
          return null;
        }

        if (page.isTombstone()) {
          //Tombstone: page was removed -> look up further, but remember the position
          if (firstTombstoneIndex < 0) {
            //first tombstone is a place to insert new item _if already existent not found_
            // -> we still need to scan until the end of probing sequence to ensure no page
            // with pageIndex exist, but then we'll use firstTombstoneIndex to insert a new page:
            firstTombstoneIndex = probeIndex;
          }
        }
        else if (page.pageIndex == pageIndex) {
          if (insertionIndexRef != null) {
            insertionIndexRef.set(probeIndex);
          }
          return page;
        }

        probeIndex = (probeIndex + probeStep) % length;
      }

      //no page found AND no space to insert -> need resize
      if (insertionIndexRef != null) {
        insertionIndexRef.set(-1);
      }
      return null;
    }

    private static int probeStep(final int length) {
      //MAYBE RC: choose something prime
      return length - 1;
    }
  }

  public static class Page implements AutoCloseable, Flushable {

    /**
     * Initial state page is created with. Page buffer is not yet allocated, page data is not yet
     * loaded. Page is not usable yet, so {usageCount=0}. It is an internally-used state, Page in
     * this state should not be visible for clients.
     * Transition to STATE_USABLE is allowed.
     * (RC: I'm thinking about allowing direct transition to ABOUT_TO_UNMAP/TOMBSTONE on storage close,
     * but not sure yet)
     */
    public static final int STATE_NOT_READY_YET = 0;
    /**
     * Page is allocated, loaded, and could be used. In this state {usageCount=0...}.
     * Only transition to STATE_ABOUT_TO_UNMAP is allowed.
     */
    public static final int STATE_USABLE = 1;
    /**
     * Page is prepared to unmap and release/reclaim. Clients who already get the Page before transition
     * to that state -- could continue to use it, but new clients are not allowed to get the Page. Hence,
     * in this state .usageCount could be >0, but could not increase anymore -- only decrease.
     * Only transition to STATE_TOMBSTONE is allowed.
     */
    public static final int STATE_ABOUT_TO_UNMAP = 2;
    /**
     * Page is unmapped, its buffer is released/reclaimed -- i.e. there no page anymore, only remnant.
     * Terminal state: no transition allowed, tombstone Page occupies slot in a PageTable until it is
     * either replaced with another Page, or removed during resize/rehash.
     */
    public static final int STATE_TOMBSTONE = 3;

    private static final AtomicIntegerFieldUpdater<Page> STATE_UPDATER = AtomicIntegerFieldUpdater.newUpdater(
      Page.class,
      "statePacked"
    );

    private static final AtomicLongFieldUpdater<Page> MODIFIED_REGION_UPDATER = AtomicLongFieldUpdater.newUpdater(
      Page.class,
      "modifiedRegionPacked"
    );

    private static final AtomicIntegerFieldUpdater<Page> TOKENS_UPDATER = AtomicIntegerFieldUpdater.newUpdater(
      Page.class,
      "tokensOfUsefulness"
    );


    private static final long EMPTY_MODIFIED_REGION = 0;

    private static final int USAGE_COUNT_MASK = 0x00FF_FFFF;

    private final int pageSize;
    /** 0-based page index == offsetInFile/pageSize */
    private final int pageIndex;
    /** Offset of the page first byte in the source file */
    private final transient long offsetInFile;

    private transient final ReentrantReadWriteLock pageLock = new ReentrantReadWriteLock();

    /**
     * The buffer position/limit shouldn't be touched, since it is hard to make that concurrent:
     * buffer content _could_ be co-used by many parties, but limit/position could not.
     * <p>
     * Access to .data content must be guarded with .pageLock. Modification of .data ref itself
     * is lock-free, protected by state transition: .data is guaranteed to be non-null in
     * [USABLE and ABOUT_TO_UNMAP] states, and should be accessed in those states only.
     */
    //@GuardedBy("pageLock")
    private ByteBuffer data;

    /**
     * Modified region [minOffsetModified, maxOffsetModified) of {@linkplain #data}, packed into a single
     * long for atomic modification.
     * <br/>
     * {@code modifiedRegionPacked = (maxOffsetModified<<32) | minOffsetModified}
     * <br/>
     * maxOffsetModified is basically buffer.position(), i.e. first byte to store, maxOffsetModified is
     * buffer.limit() -- i.e. the _next_ byte after the last byte to store.
     */
    private volatile long modifiedRegionPacked = EMPTY_MODIFIED_REGION;

    private final @NotNull FilePageCacheLockFree.PageToStorageHandle pageToStorageHandle;

    /**
     * Page state, as (state, usageCount), packed into a single int for atomic update. Highest 1 byte
     * is state{STATE_NOT_READY_YET | STATE_USABLE | STATE_OUT}, lowest 3 bytes ({@link #USAGE_COUNT_MASK})
     * are usageCount -- number of clients currently using this page ({@link #tryAcquireForUse(Object)}/{@link #release()})
     */
    private volatile int statePacked = 0;


    private volatile int tokensOfUsefulness = TOKENS_INITIALLY;//for reclamation management
    private int tokensOfUsefulnessLocal = 0;//for reclamation management

    protected Page(final long offsetInFile,
                   final int pageIndex,
                   final int pageSize,
                   final @Nullable ByteBuffer data,
                   final @NotNull PageToStorageHandle pageToStorageHandle) {
      if (offsetInFile < 0) {
        throw new IllegalArgumentException("offsetInFile(=" + offsetInFile + ") must be >=0");
      }
      if (pageIndex < 0) {
        throw new IllegalArgumentException("pageIndex(=" + pageIndex + ") must be >=0");
      }
      if (pageSize <= 0) {
        throw new IllegalArgumentException("pageSize(=" + pageSize + ") must be >0");
      }
      this.offsetInFile = offsetInFile;
      this.pageIndex = pageIndex;
      this.pageSize = pageSize;
      this.data = data;
      this.pageToStorageHandle = pageToStorageHandle;
    }

    protected Page(final int pageIndex,
                   final int pageSize,
                   final @Nullable ByteBuffer data,
                   final @NotNull PageToStorageHandle pageToStorageHandle) {
      this(pageIndex * (long)pageSize, pageIndex, pageSize, data, pageToStorageHandle);
    }

    public static Page notReady(final int index,
                                final int pageSize,
                                final @NotNull PageToStorageHandle pageToStorageHandle) {
      final Page page = new Page(index, pageSize, null, pageToStorageHandle);
      page.statePacked = packState(STATE_NOT_READY_YET, 0);
      return page;
    }

    public int pageSize() {
      return pageSize;
    }

    public int pageIndex() { return pageIndex; }

    public long offsetInFile() {
      return offsetInFile;
    }

    public long lastOffsetInFile() {
      return (pageIndex + 1L) * pageSize - 1;
    }


    //===== locking methods: ====================================

    public void lockPageForWrite() {
      pageLock.writeLock().lock();
    }

    public void unlockPageForWrite() {
      pageLock.writeLock().unlock();
    }

    public void lockPageForRead() {
      pageLock.readLock().lock();
    }

    public void unlockPageForRead() {
      pageLock.readLock().unlock();
    }

    //========= Page.state management methods: ==================

    protected boolean isNotReadyYet() {
      return inState(STATE_NOT_READY_YET);
    }

    public boolean isUsable() {
      return inState(STATE_USABLE);
    }

    public boolean isAboutToUnmap() {
      return inState(STATE_ABOUT_TO_UNMAP);
    }

    public boolean isTombstone() {
      return inState(STATE_TOMBSTONE);
    }

    protected boolean inState(final int expectedState) {
      return unpackState(statePacked) == expectedState;
    }

    protected int usageCount() {
      return unpackUsageCount(this.statePacked);
    }

    /**
     * [NOT_READY_YET => USABLE] page transition: sets buffer with data, and set state to
     * {@linkplain #STATE_USABLE}. Page must be in {@linkplain #STATE_NOT_READY_YET} before
     * call to this method, otherwise {@linkplain AssertionError} will be thrown
     */
    protected <E extends Exception> void prepareForUse(final @NotNull PageContentLoader contentLoader) throws IOException {
      pageLock.writeLock().lock();
      try {
        if (!inState(STATE_NOT_READY_YET)) {
          throw new AssertionError("Bug: page must be NOT_READY_YET for .prepareToUse(), but: " + this);
        }

        this.data = contentLoader.loadPageContent(this);
        this.statePacked = packState(STATE_USABLE, 0);
      }
      finally {
        pageLock.writeLock().unlock();
      }
    }

    /** Tries to acquire page for use -- i.e. */
    protected boolean tryAcquireForUse(final Object acquirer) throws IOException {
      while (true) {//CAS loop:
        final int packedState = statePacked;
        final int state = unpackState(packedState);
        final int usageCount = unpackUsageCount(packedState);
        if (state == STATE_NOT_READY_YET) {
          return false;
        }
        if (state != STATE_USABLE) {
          throw new IOException("Page.state[=" + state + "] != USABLE");
        }
        final int newUsageCount = usageCount + 1;
        if (newUsageCount > USAGE_COUNT_MASK) {
          throw new AssertionError("Too many usages: " + newUsageCount
                                   + " (max: " + USAGE_COUNT_MASK + ") -- likely .release() call is missed");
        }
        final int newPackedState = packState(state, newUsageCount);
        if (STATE_UPDATER.compareAndSet(this, packedState, newPackedState)) {
          return true;
        }
      }
    }

    public void release() {
      while (true) {
        final int packedState = statePacked;
        final int state = unpackState(packedState);
        final int usageCount = unpackUsageCount(packedState);
        if (state == STATE_NOT_READY_YET || state == STATE_TOMBSTONE) {
          throw new AssertionError("Bug: .release() must be called on {USABLE|ABOUT_TO_UNMAP} page only, but .state[=" + state + "]");
        }
        if (usageCount == 0) {
          throw new AssertionError("Bug: can't .release() page with usageCount=0 -- unpaired .acquire()/.release() calls?");
        }
        final int newUsageCount = usageCount - 1;
        final int newPackedState = packState(state, newUsageCount);
        if (STATE_UPDATER.compareAndSet(this, packedState, newPackedState)) {
          addTokensOfUsefulness(TOKENS_PER_USE * usageCount);
          break;
        }
      }
    }

    /**
     * Moves page along the state transition graph towards TOMBSTONE state as much as it is possible.
     * Returns the final state reached, if at least 1 transition was done, or -1 if no transitions are
     * available in the current state.
     */
    protected int tryMoveTowardsTomb(final boolean entombYoung) {
      int lastSuccessMove = -1;
      while (true) {
        final int packedState = statePacked;
        final int state = unpackState(packedState);
        final int usageCount = unpackUsageCount(packedState);
        switch (state) {
          case STATE_NOT_READY_YET: {
            if (entombYoung) {
              if (!pageLock.writeLock().isHeldByCurrentThread()) {
                throw new AssertionError(".writeLock must be held for entombYoung[NOT_READY_YET=>TOMBSTONE] transition");
              }
              final int newPackedState = packState(STATE_TOMBSTONE, 0);
              if (STATE_UPDATER.compareAndSet(this, packedState, newPackedState)) {
                return STATE_TOMBSTONE;
              }
            }
            return lastSuccessMove;
          }
          case STATE_USABLE: {
            final int newPackedState = packState(STATE_ABOUT_TO_UNMAP, usageCount);
            if (STATE_UPDATER.compareAndSet(this, packedState, newPackedState)) {
              lastSuccessMove = STATE_ABOUT_TO_UNMAP;
              continue;
            }
          }
          case STATE_ABOUT_TO_UNMAP: {
            if (usageCount > 0) {
              return lastSuccessMove;
            }
            final int newPackedState = packState(STATE_TOMBSTONE, 0);
            if (STATE_UPDATER.compareAndSet(this, packedState, newPackedState)) {
              return STATE_TOMBSTONE;
            }
          }
          case STATE_TOMBSTONE: {
            return lastSuccessMove;
          }
          default: {
            throw new AssertionError("Code bug: unknown state " + state + ": " + this);
          }
        }
      }
    }

    private static int packState(final int state,
                                 final int newUsageCount) {
      return (state << 24) | newUsageCount;
    }

    private static int unpackUsageCount(final int packedState) {
      return packedState & USAGE_COUNT_MASK;
    }

    private static int unpackState(final int packedState) {
      return packedState >> 24;
    }

    // ====================================================================

    @Override
    public void close() {
      release();
    }

    // ====================================================================

    protected int addTokensOfUsefulness(final int tokensToAdd) {
      assert tokensToAdd >= 0 : "tokensToAdd(" + tokensToAdd + ") must be >=0";
      while (true) {//CAS loop:
        final int tokens = tokensOfUsefulness;
        int newTokens = tokens + tokensToAdd;
        if (newTokens < 0) {//protect from overflow
          newTokens = Integer.MAX_VALUE;
        }
        if (TOKENS_UPDATER.compareAndSet(this, tokens, newTokens)) {
          return newTokens;
        }
      }
    }

    protected int decayTokensOfUsefulness(final int numerator,
                                          final int denominator) {
      assert numerator >= 0 : "numerator(" + numerator + ") must be >=0";
      assert denominator > 0 : "denominator(" + denominator + ") must be >0";
      while (true) {//CAS loop:
        final int tokens = tokensOfUsefulness;
        final int decayedTokens = tokens * numerator / denominator;
        if (TOKENS_UPDATER.compareAndSet(this, tokens, decayedTokens)) {
          return decayedTokens;
        }
      }
    }

    protected int tokensOfUsefulness() {
      return tokensOfUsefulness;
    }

    // ====================================================================

    public void regionModified(final int startOffsetModified,
                               final int length) {
      lockPageForWrite();
      try {
        final long modifiedRegion = modifiedRegionPacked;
        final int minOffsetModified = unpackMinOffsetModified(modifiedRegion);
        final int maxOffsetModified = unpackMaxOffsetModifiedExclusive(modifiedRegion);

        final int minOffsetModifiedNew = Math.min(minOffsetModified, startOffsetModified);
        final int endOffset = startOffsetModified + length;
        final int maxOffsetModifiedNew = Math.max(maxOffsetModified, endOffset);

        final long modifiedRegionNew = ((long)minOffsetModifiedNew)
                                       | (((long)maxOffsetModifiedNew) << Integer.SIZE);
        this.modifiedRegionPacked = modifiedRegionNew;

        pageToStorageHandle.modifiedRegionUpdated(
          offsetInFile() + startOffsetModified,
          length
        );
        if (modifiedRegion == 0 && modifiedRegionNew != 0) {
          pageToStorageHandle.pageBecomeDirty();
        }
      }
      finally {
        unlockPageForWrite();
      }
    }

    private static int unpackMaxOffsetModifiedExclusive(final long modifiedRegionPacked) {
      return (int)(modifiedRegionPacked >> Integer.SIZE);
    }

    private static int unpackMinOffsetModified(final long modifiedRegionPacked) {
      return (int)modifiedRegionPacked;
    }

    public boolean isDirty() {
      return modifiedRegionPacked != EMPTY_MODIFIED_REGION;
    }

    @Override
    public void flush() throws IOException {
      if (isDirty()) {
        //RC: flush is logically a read-op, hence only readLock is needed. But we also need to update
        //    a modified region, which is implemented with CAS. Together this (readLock + CAS update)
        //    implicitly avoids concurrent flushes: by holding readLock we already ensure nobody
        //    _modifies_ page concurrently with us. The only possible contender is another .flush()
        //    call -- and by CAS-ing modified region to empty _before_ actual flushing we ensure such
        //    competing .flush() will short-circuit.
        lockPageForRead();
        try {
          while (true) {//CAS loop for modifiedRegion:
            final long modifiedRegion = modifiedRegionPacked;
            if (modifiedRegion == EMPTY_MODIFIED_REGION) {
              //competing flush: somebody else already did the job
              return;
              //FIXME RC: here is the race which leads to storage.isDirty() only eventually consistent
              //          Race is going like this: lets have storage with single dirty page. thread_1
              //          & thread_2 compete for page.flush(). thread_1 wins the race, and continue
              //          towards .pageBecomeClean(), but not reach it yet (i.e. because of IO).
              //          thread_2 lose the race, and exits .flush(), and checks storage.isDirty()
              //          which is still true, since thread_1 is not yet reached .pageBecomeClean()
              //          Probably solution: use separate exclusive lock for flush, and assign region=EMPTY
              //          only at the end of the method, after .pageBecomeClean()
            }
            final int minOffsetModified = unpackMinOffsetModified(modifiedRegion);
            final int maxOffsetModifiedExclusive = unpackMaxOffsetModifiedExclusive(modifiedRegion);
            if (MODIFIED_REGION_UPDATER.compareAndSet(this, modifiedRegion, EMPTY_MODIFIED_REGION)) {
              //we won the CAS competition: execute the flush
              final ByteBuffer sliceToSave = data.duplicate();
              sliceToSave.position(minOffsetModified)
                .limit(maxOffsetModifiedExclusive);

              pageToStorageHandle.flushBytes(sliceToSave, offsetInFile + minOffsetModified);
              pageToStorageHandle.pageBecomeClean();
              return;
            }
          }
        }
        finally {
          unlockPageForRead();
        }
      }
    }


    /* =============== content access methods: ======================================================== */


    public <OUT, E extends Exception> OUT read(final int startOffset,
                                               final int length,
                                               final ThrowableNotNullFunction<ByteBuffer, OUT, E> reader) throws E {
      pageLock.readLock().lock();
      try {
        checkPageIsValidForAccess();

        //RC: need to upgrade language level to 13 to use .slice(index, length)
        final ByteBuffer slice = data.duplicate()
          .asReadOnlyBuffer();
        slice.position(startOffset)
          .limit(startOffset + length);
        return reader.fun(slice);
      }
      finally {
        pageLock.readLock().unlock();
      }
    }

    public <OUT, E extends Exception> OUT write(final int startOffset,
                                                final int length,
                                                final ThrowableNotNullFunction<ByteBuffer, OUT, E> writer) throws E {
      pageLock.writeLock().lock();
      try {
        checkPageIsValidForAccess();

        //RC: need to upgrade language level to 13 to use .slice(index, length)
        final ByteBuffer slice = data.duplicate();
        slice.position(startOffset)
          .limit(startOffset + length);
        return writer.fun(slice);
      }
      finally {
        regionModified(startOffset, length);
        pageLock.writeLock().unlock();
      }
    }

    public byte get(final int offsetInPage) {
      lockPageForRead();
      try {
        checkPageIsValidForAccess();

        return data.get(offsetInPage);
      }
      finally {
        unlockPageForRead();
      }
    }

    public int getInt(final int offsetInPage) {
      lockPageForRead();
      try {
        checkPageIsValidForAccess();

        return data.getInt(offsetInPage);
      }
      finally {
        unlockPageForRead();
      }
    }

    public long getLong(final int offsetInPage) {
      lockPageForRead();
      try {
        checkPageIsValidForAccess();

        return data.getLong(offsetInPage);
      }
      finally {
        unlockPageForRead();
      }
    }

    public void readToArray(final byte[] destination,
                            final int offsetInArray,
                            final int offsetInPage,
                            final int length) {
      lockPageForRead();
      try {
        checkPageIsValidForAccess();

        ByteBufferUtil.copyMemory(data, offsetInPage, destination, offsetInArray, length);
      }
      finally {
        unlockPageForRead();
      }
    }


    public void put(final int offsetInPage,
                    final byte value) {
      lockPageForWrite();
      try {
        checkPageIsValidForAccess();

        data.put(offsetInPage, value);
        regionModified(offsetInPage, Byte.BYTES);
      }
      finally {
        unlockPageForWrite();
      }
    }

    public void putInt(final int offsetInPage,
                       final int value) {
      lockPageForWrite();
      try {
        checkPageIsValidForAccess();

        data.putInt(offsetInPage, value);
        regionModified(offsetInPage, Integer.BYTES);
      }
      finally {
        unlockPageForWrite();
      }
    }

    public void putLong(final int offsetInPage,
                        final long value) {
      lockPageForWrite();
      try {
        checkPageIsValidForAccess();

        data.putLong(offsetInPage, value);
        regionModified(offsetInPage, Long.BYTES);
      }
      finally {
        unlockPageForWrite();
      }
    }

    public void putFromBuffer(final ByteBuffer data,
                              final int offsetInPage) {
      lockPageForWrite();
      try {
        checkPageIsValidForAccess();

        //RC: since java16 may use this.data.put(offsetInPage, data, 0, data.remaining());
        final int length = data.remaining();
        final ByteBuffer buf = this.data.duplicate();
        buf.position(offsetInPage);
        buf.put(data);
        regionModified(offsetInPage, length);
      }
      finally {
        unlockPageForWrite();
      }
    }

    public void putFromArray(final byte[] source,
                             final int offsetInArray,
                             final int offsetInPage,
                             final int length) {
      lockPageForWrite();
      try {
        checkPageIsValidForAccess();

        final ByteBuffer buf = data.duplicate();
        buf.position(offsetInPage);
        buf.put(source, offsetInArray, length);
        regionModified(offsetInPage, length);
      }
      finally {
        unlockPageForWrite();
      }
    }

    /**
     * Checks page in a state there it could be read/write, throws {@link IllegalStateException}
     * otherwise
     *
     * @throws IllegalStateException if page is not in a state there it could be accessed (read/write)
     */
    private void checkPageIsValidForAccess() {
      if (!isUsable() && !isAboutToUnmap()) {
        throw new IllegalStateException("Page state must be in { USABLE | ABOUT_TO_UNMAP } for accessing, but: " + this);
      }
      if (usageCount() == 0) {
        throw new IllegalStateException("Page must be acquired for use (i.e. usageCount>0) before accessing, but: " + this);
      }
    }

    @Override
    public String toString() {
      return "Page[#" + pageIndex + ", size: " + pageSize + "b, offsetInFile: " + offsetInFile + "b]" +
             "{" +
             "state: " + unpackState(statePacked) + ", " +
             "inUse: " + unpackUsageCount(statePacked) + ", " +
             "dirty: " + isDirty() +
             "}";
    }
  }

  /**
   * Page is 'owned' by a storage, and storage needs to be notified about some state transitions
   * happened in Page, and also storage links Page to an appropriate region of a file, which is
   * needed for flush(). This interface abstracts such a relationship.
   */
  interface PageToStorageHandle {
    void pageBecomeDirty();

    void pageBecomeClean();

    void modifiedRegionUpdated(final long startOffsetInFile,
                               final int length);

    /** Writes buffer content (between position and limit) into the file at offsetInFile position */
    void flushBytes(final @NotNull ByteBuffer dataToFlush,
                    final long offsetInFile) throws IOException;
  }

  /**
   * Storages could 'ask' FilePageCache to do something asynchronously. This is the base class
   * for such requests.
   * RC: Class is empty, since now there is only one impl of this class, and I'm not yet sure
   * which API is worth to have in a base class.
   */
  protected static abstract class Command {
  }

  /**
   * Command issued by {@link PagedFileStorageLockFree} on a close -- request FilePageCache to clean
   * up all used data of the storage.
   */
  protected static class CloseStorageCommand extends Command {
    private final PagedFileStorageLockFree storageToClose;
    private final CompletableFuture<?> onFinish;

    protected CloseStorageCommand(final @NotNull PagedFileStorageLockFree storageToClose,
                                  final @NotNull CompletableFuture<Object> onFinish) {
      this.storageToClose = storageToClose;
      this.onFinish = onFinish;
    }
  }

  private static class PagesForReclaimCollector {

    public static final Comparator<Page> BY_USEFULNESS = comparing(page -> page.tokensOfUsefulnessLocal);
    /**
     * Keeps and updates a 'low-usefulness' threshold: i.e. which value of page.tokensOfUsefulness
     * considered 'low', so the pages with usefulness below it are considered candidates for reclamation.
     * By default, 'low-usefulness' is the bottom 10% of all pages, but this could be increased if
     * page allocation/reclamation pressure is high.
     */
    private final FrugalQuantileEstimator lowUsefulnessThresholdEstimator;
    private final int minPercentOfPagesToPrepareForReclaim;
    private final int maxPercentOfPagesToPrepareForReclaim;

    private @NotNull List<Page> pagesForReclaimNonDirty = Collections.emptyList();
    private @NotNull List<Page> pagesForReclaimDirty = Collections.emptyList();

    public PagesForReclaimCollector(final int minPercentOfPagesToPrepareForReclaim,
                                    final int maxPercentOfPagesToPrepareForReclaim) {
      if (minPercentOfPagesToPrepareForReclaim > maxPercentOfPagesToPrepareForReclaim) {
        throw new IllegalArgumentException("minPercent(=" + minPercentOfPagesToPrepareForReclaim + ") must be <= " +
                                           "maxPercent(=" + maxPercentOfPagesToPrepareForReclaim + ")");
      }
      this.minPercentOfPagesToPrepareForReclaim = minPercentOfPagesToPrepareForReclaim;
      this.maxPercentOfPagesToPrepareForReclaim = maxPercentOfPagesToPrepareForReclaim;
      lowUsefulnessThresholdEstimator = new FrugalQuantileEstimator(
        minPercentOfPagesToPrepareForReclaim,
        /* step:    */ 0.5,
        /* initial: */ 0
      );
    }

    //TODO RC: pass in Iterable<Page> instead of Map<Page>

    public void startCollectingTurn() {
      pagesForReclaimDirty = new ArrayList<>(5);
      pagesForReclaimNonDirty = new ArrayList<>(5);
    }

    public void finishCollectingTurn() {  //FIXME: sorting by volatile field is unstable!
      pagesForReclaimDirty.sort(BY_USEFULNESS);
      pagesForReclaimNonDirty.sort(BY_USEFULNESS);
    }

    public List<Page> pagesForReclaimNonDirty() { return pagesForReclaimNonDirty; }

    public List<Page> pagesForReclaimDirty() { return pagesForReclaimDirty; }

    public ConcurrentLinkedQueue<Page> pagesForReclaimAsQueue() {
      final ConcurrentLinkedQueue<Page> pagesForReclaim = new ConcurrentLinkedQueue<>();
      pagesForReclaim.addAll(pagesForReclaimNonDirty);
      pagesForReclaim.addAll(pagesForReclaimDirty);
      return pagesForReclaim;
    }

    public int totalPagesPreparedToReclaim() {
      return pagesForReclaimDirty.size() + pagesForReclaimNonDirty.size();
    }

    public void decreasePercentage() {
      final int currentPercentage = lowUsefulnessThresholdEstimator.percentileToEstimate();
      if (currentPercentage > minPercentOfPagesToPrepareForReclaim) {
        lowUsefulnessThresholdEstimator.updateTargetPercentile(currentPercentage - 1);
      }
    }

    public void increasePercentage() {
      final int currentPercentage = lowUsefulnessThresholdEstimator.percentileToEstimate();
      if (currentPercentage < maxPercentOfPagesToPrepareForReclaim) {
        lowUsefulnessThresholdEstimator.updateTargetPercentile(currentPercentage + 1);
      }
    }

    /**
     * Try to ensure cleanPagesForReclaim/totalPagesForReclaim >= fractionOfCleanPagesToAim
     * If there are fewer clean pages than requested -> try to flush some dirty pages, until
     * the fractionOfCleanPagesToAim is satisfied.
     */
    public void ensureEnoughCleanPagesToReclaim(final double fractionOfCleanPagesToAim) {
      final int totalPagesToReclaim = pagesForReclaimDirty.size() + pagesForReclaimNonDirty.size();
      final int dirtyPagesTargetCount = (int)((1 - fractionOfCleanPagesToAim) * totalPagesToReclaim);
      final int pagesToFlush = pagesForReclaimDirty.size() - dirtyPagesTargetCount;
      if (pagesToFlush > 0) {
        for (int i = 0; i < pagesToFlush; i++) {
          final Page page = pagesForReclaimDirty.get(i);
          if (page.isTombstone()) {
            continue;
          }
          try {
            //TODO RC: flush() could be off-loaded to IO thread pool, instead of slowing down housekeeper
            //         thread
            page.flush();
          }
          catch (IOException e) {
            LOG.warn("Can't flush page " + page, e);
          }
        }
      }
    }

    private void checkPageGoodForReclaim(final @NotNull Page page) {
      final int tokensOfUsefulness = page.tokensOfUsefulness();
      final double lowUsefulnessThreshold = lowUsefulnessThresholdEstimator.updateEstimation(tokensOfUsefulness);
      if (page.isUsable()
          && page.usageCount() == 0
          && tokensOfUsefulness <= lowUsefulnessThreshold) {
        addCandidateForReclaim(page);
      }
    }

    private void addCandidateForReclaim(final @NotNull Page page) {
      if (page.isDirty()) {
        pagesForReclaimDirty.add(page);
      }
      else {
        pagesForReclaimNonDirty.add(page);
      }
    }
  }
}
