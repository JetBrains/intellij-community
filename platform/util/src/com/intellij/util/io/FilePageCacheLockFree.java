// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.io.pagecache.FilePageCacheStatistics;
import com.intellij.util.io.pagecache.impl.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static com.intellij.util.MathUtil.clamp;
import static com.intellij.util.containers.ContainerUtil.intersection;
import static com.intellij.util.containers.ContainerUtil.subtract;
import static java.util.Comparator.comparing;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Maintains 'pages' of data (in the form of {@linkplain PageImpl}), from file storages {@linkplain PagedFileStorageWithRWLockedPageContent}.
 * <br>
 * This class is not a file cache per se (this is {@linkplain PagesTable}) but mostly a cache housekeeper:
 * it does background jobs to keep pages cache at bay. I.e. it maintains a pool of buffers, limits the
 * total size of buffers in use, does async flushing, flushes and re-uses the least used pages, and so on.
 * <br>
 * Page cache delegates {@link ByteBuffer buffer}s management to {@link IMemoryManager} -- which should take
 * care of buffers caching, and keep limits on total memory (heap/native) used by the cache.
 * As the limit(s) are reached, oldest and/or least used pages are evicted from cache (and flushed to disk).
 * Pages also evicted with their owner {@linkplain PagedFileStorageWithRWLockedPageContent} re-registration
 * {@linkplain #enqueueStoragePagesClosing(PagedFileStorageWithRWLockedPageContent, CompletableFuture)}
 * <p>
 */
@ApiStatus.Internal
public final class FilePageCacheLockFree implements AutoCloseable {
  private static final Logger LOG = Logger.getInstance(FilePageCacheLockFree.class);

  public static final String DEFAULT_HOUSEKEEPER_THREAD_NAME = "FilePageCache housekeeper";

  /** Initial size of page table hashmap in {@linkplain PagesTable} */
  private static final int INITIAL_PAGES_TABLE_SIZE = 1 << 8;

  /** Before housekeeper thread created */
  private static final int STATE_NOT_STARTED = 0;
  /**
   * Housekeeper thread is created, cache is operational -- but thread is not started, because no
   * storage registers its pages with the cache for housekeeping yet.
   */
  private static final int STATE_WAITING_FIRST_STORAGE_REGISTRATION = 1;
  /** Housekeeper thread started, cache is operational */
  private static final int STATE_WORKING = 2;
  /** Housekeeper thread stopped, cache is closed, new accesses are prohibited */
  private static final int STATE_CLOSED = 3;

  /**
   * 'Tokens of usefulness' are added to the page each time page is acquired for use, and also
   * each time housekeeper thread finds page in use (per-use)
   */
  public static final int TOKENS_PER_USE = 8;
  /** Tokens initially assigned to the new page */
  public static final int TOKENS_INITIALLY = 2 * TOKENS_PER_USE;

  /** Housekeeper thread collects ~10% of the least useful pages for reclaim */
  private static final int DEFAULT_PERCENTS_OF_PAGES_TO_PREPARE_FOR_RECLAIM = 10;

  /** Max pages to try reclaiming during new page allocation, before fallback to heap-page allocation */
  private static final int MAX_PAGES_TO_TRY_RECLAIM_ON_ALLOCATION = 10;



  /* ================= instance fields ============================================================== */

  private final IMemoryManager memoryManager;

  //@GuardedBy("pagesPerStorage")
  private final Map<Path, PagesTable> pagesPerFile = CollectionFactory.createSmallMemoryFootprintMap();
  /** Must be used only be housekeeper thread, so no sync required */
  private final CyclicIterator<PagesTable> pageTableCyclicIterator = new CyclicIterator<>();

  /**
   * Collection of pages that are likely the best suited to reclaim -- i.e. to unmap, and re-use their
   * buffer for another page needs to be loaded. To fill the collection, housekeeper thread periodically
   * scans all the pages and selects the pages that are not in use right now, and used the least
   * recently.
   * Bear in mind that page state changes asynchronously, so this collection is not exactly up-to-date.
   * I.e., pages in this collection are just 'likely' to be the ones ready to reclaim -- it is possible
   * that somebody starts using a page after it was put in, and hence becomes not eligible to reclaim
   * anymore. So each page must be carefully inspected before reclaiming it.
   */
  private final PagesToReclaim pagesToProbablyReclaim = new PagesToReclaim();

  private final PagesForReclaimCollector pagesForReclaimCollector = new PagesForReclaimCollector(
    DEFAULT_PERCENTS_OF_PAGES_TO_PREPARE_FOR_RECLAIM,
    2 * DEFAULT_PERCENTS_OF_PAGES_TO_PREPARE_FOR_RECLAIM
  );

  private final RateController rateController = new RateController();

  private final ConcurrentLinkedQueue<Command> commandsQueue = new ConcurrentLinkedQueue<>();


  private final Thread housekeeperThread;

  /**
   * Housekeeper thread waits on this lock on in between turns.
   * Other threads could notify it to make the housekeeper wake up earlier.
   */
  private final Object housekeeperSleepLock = new Object();

  /** PageCache lifecycle state */
  @SuppressWarnings("UnusedAssignment")
  private volatile int state = STATE_NOT_STARTED;

  private final FilePageCacheStatistics statistics = new FilePageCacheStatistics();


  public FilePageCacheLockFree(final long cacheCapacityBytes) {
    this(cacheCapacityBytes, r -> new Thread(r, DEFAULT_HOUSEKEEPER_THREAD_NAME));
  }

  public FilePageCacheLockFree(final long cacheCapacityBytes,
                               final ThreadFactory maintenanceThreadFactory) {
    this.memoryManager = new DefaultMemoryManager(
      cacheCapacityBytes,
      cacheCapacityBytes / 10, // allow allocating up to 10% buffers from the heap
      statistics
    );


    housekeeperThread = maintenanceThreadFactory.newThread(this::cacheMaintenanceLoop);
    housekeeperThread.setDaemon(true);

    state = STATE_WAITING_FIRST_STORAGE_REGISTRATION;
  }

  public long getCacheCapacityBytes() {
    return memoryManager.nativeCapacityBytes();
  }

  /**
   * Method registers storage in a file page cache, and returns PageTable, maintained by this cache.
   * There is no paired 'unregister' method -- instead there is {@link #enqueueStoragePagesClosing(PagedFileStorageWithRWLockedPageContent, CompletableFuture)}
   * method which storage should call to ask page cache to do a cleanup as a part of maintenance
   * and housekeeping.
   */
  public PagesTable registerStorage(final @NotNull PagedFileStorageWithRWLockedPageContent storage) throws IOException {
    checkNotClosed();
    synchronized (pagesPerFile) {
      final Path absolutePath = storage.getFile().toAbsolutePath();
      if (pagesPerFile.containsKey(absolutePath)) {
        throw new IOException("Storage for [" + absolutePath + "] is already registered");
      }

      final boolean firstStorageRegistered = pagesPerFile.isEmpty();

      final PagesTable pages = new PagesTable(INITIAL_PAGES_TABLE_SIZE);
      pagesPerFile.put(absolutePath, pages);

      if (firstStorageRegistered && state == STATE_WAITING_FIRST_STORAGE_REGISTRATION) {
        //Don't start housekeeper thread before actual storage is registered: there are a lot of
        // FPCaches created in static fields 'just in case', but not really used -- not worth to
        // spent running threads for them.
        housekeeperThread.start();
        state = STATE_WORKING;
      }
      return pages;
    }
  }

  Future<?> enqueueStoragePagesClosing(final @NotNull PagedFileStorageWithRWLockedPageContent storage,
                                       final @NotNull CompletableFuture<Object> finish) {
    checkNotClosed();
    final PostCloseStorageCleanupCommand task = new PostCloseStorageCleanupCommand(storage, finish);
    commandsQueue.add(task);
    return task.onFinish;
  }

  @Override
  public void close() throws InterruptedException {
    synchronized (this) { //avoid concurrent .close()
      if (state != STATE_CLOSED) {
        housekeeperThread.interrupt();
        housekeeperThread.join();
        state = STATE_CLOSED;
      }
    }
  }

  public FilePageCacheStatistics getStatistics() {
    statistics.nativeBytesCurrentlyUsed(memoryManager.nativeBytesUsed());
    statistics.heapBytesCurrentlyUsed(memoryManager.heapBytesUsed());
    return statistics;
  }

  public String dumpContent() {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<Path, PagesTable> e : pagesPerFile.entrySet()) {
      Path path = e.getKey();
      PagesTable pagesTable = e.getValue();


      AtomicReferenceArray<PageImpl> pages = pagesTable.pages();
      int pagesCount = 0;
      int pageSize = 0;
      for (int i = 0; i < pages.length(); i++) {
        PageImpl page = pages.get(i);
        if (page != null) {
          pageSize = page.pageSize();
          if (!page.isTombstone()) {
            pagesCount++;
          }
        }
      }

      sb.append('[').append(path).append("]: pageSize=").append(pageSize).append('\n');
      sb.append("\tpagesTable: ")
        .append(pagesCount).append(" live out of ").append(pages.length())
        .append('\n');
      sb.append(pagesTable.probeLengthsHistogram()).append('\n');
    }
    return sb.toString();
  }

  private final Throttler releaseMemoryOverflowThrottler = new Throttler(100, MILLISECONDS);


  private void cacheMaintenanceLoop() {
    while (!Thread.interrupted()) {
      long turnStartedAtNs = System.nanoTime();

      try {
        boolean actuallyDidSomeMaintenance = false;

        if (!commandsQueue.isEmpty()) {
          //Closed storages are the easiest way to reclaim pages -- eat that low-hanging fruit first:
          int reclaimed = cleanClosedStoragesAndReclaimPages(/* max per turn: */ 1);

          statistics.closedStoragesReclaimed(reclaimed);
          actuallyDidSomeMaintenance = true;
        }


        //ensure queue is fresh enough -- drop out pages not good enough for reclaim anymore:
        //TODO RC: throttle it each ~5ms? Right now it is useless optimization, because we need
        //         reclaimQueue.size() anyway, and it is basically the same scanning through the queue.
        //         But as soon as we'll have non-O(N) size impl -- this code may be throttled, for sure
        int pagesRemainedForReclaim = 0;
        int pagesDroppedFromReclaimQueue = 0;
        for (Iterator<PageImpl> it = pagesToProbablyReclaim.pagesToProbablyReclaimQueue.iterator();
             it.hasNext(); ) {
          PageImpl page = it.next();
          if (!pagesForReclaimCollector.isGoodForReclaim(page)) {
            it.remove();
            pagesDroppedFromReclaimQueue++;
          }
          else {
            pagesRemainedForReclaim++;
          }
        }

        int pagesDemandForecast = rateController.predictPagesDemandForNextTurn();

        boolean pageDeficitIsLikely = isPageDeficitLikely(pagesRemainedForReclaim, pagesDemandForecast);
        if (pageDeficitIsLikely
            || pagesToProbablyReclaim.isOlderThen(turnStartedAtNs, MILLISECONDS.toNanos(500))) {
          //We collect pages for reclaim based on their recent utility -- but this utility changes
          // with time. Pages good-for-reclaim a second ago -- may become not-so-good for reclaim
          // anymore now. Hence, we need to refresh pagesToReclaim if exhausted -- but also regularly

          refillPagesForReclaim(pagesDemandForecast);

          //if <50% of pages collected to reclaim are clean -> try to eagerly flush some dirty pages:
          //MAYBE RC: flush 50% seems to be an overkill -- better to flush ~pagesToCollect. Since we
          //  collect pages well in advance, i.e. way above forecasted demand in next 1ms, flushing
          //  50% is an overkill, since it is likely most of those pages won't be reclaimed until
          //  next queue refill, or they become dirty again.
          int pagesFlushed = pagesForReclaimCollector.ensureEnoughCleanPagesToReclaim(0.5);

          actuallyDidSomeMaintenance = true;

          pagesRemainedForReclaim = pagesForReclaimCollector.totalPagesPreparedToReclaim();
          pageDeficitIsLikely = isPageDeficitLikely(pagesRemainedForReclaim, pagesDemandForecast);
        }

        if (memoryManager.hasOverflow()) {
          boolean executed = releaseMemoryOverflowThrottler.runThrottled(
            turnStartedAtNs,
            () -> releasePagesAllocatedAboveCapacity(/*maxPagesToRelease: */ 10)
          );

          actuallyDidSomeMaintenance |= executed;
        }


        long timeSpentNs = System.nanoTime() - turnStartedAtNs;

        if (actuallyDidSomeMaintenance) {
          statistics.cacheMaintenanceTurnDone(timeSpentNs);
        }
        else {
          statistics.cacheMaintenanceTurnSkipped(timeSpentNs);
        }


        //Assess allocation pressure and adjust our efforts:
        if (!pageDeficitIsLikely) {
          //allocation pressure low: could collect less and sleep more
          pagesForReclaimCollector.collectLessAggressively();
          synchronized (housekeeperSleepLock) {
            //MAYBE RC: we could predict ~how soon we'll need refill -- based on current reclaimQueue size
            //          and the load forecast -- and increase .wait() time accordingly, e.g. in [1..10]ms
            //          This should reduce CPU consumption in case of low load.
            housekeeperSleepLock.wait(1);
          }
        }
        else if (pagesRemainedForReclaim > 0) {
          //allocation pressure high, but we ~catch up with it: just yield
          Thread.yield();
        }
        else {
          //allocation pressure is so high we can't catch up with it:
          // 1) no time to wait
          // 2) must collect more pages-to-reclaim
          pagesForReclaimCollector.collectMoreAggressively();
        }
      }
      catch (InterruptedException e) {
        break;
      }
      catch (Throwable t) {
        LOG.error("Exception in FilePageCache housekeeper thread (thread continue to run)", t);
      }
    }
    LOG.info("maintenance loop interrupted -> exiting");
  }

  private boolean isPageDeficitLikely(int pagesRemainedForReclaim,
                                      int pagesDemandForecast) {
    boolean memoryBudgetIsAboutToExhaust = memoryManager.nativeBytesUsed() > 4 * memoryManager.nativeCapacityBytes() / 5;
    boolean pagesPileIsAboutToExhaust = pagesDemandForecast > pagesRemainedForReclaim;
    return pagesPileIsAboutToExhaust && memoryBudgetIsAboutToExhaust;
  }

  //TODO RC:
  //     1. Use QuantilesEstimator(80-90%)Trace allocation pressure: (pagesToReclaim (before) - pagesToReclaimQueue.size), and use
  //         to estimate percentage of pages to prepare to reclaim.
  //        Also use it to estimate how much 'eager flushes' needs to be done.
  //     2. In .allocatePageBuffer(): trace counts of .flush() invoked
  //     3. If a lot of .flush() invoked -> increase probability of 'eager flushes' in (2)
  //     4. Optimize pagesToProbablyReclaimQueue: use fixed-size ring buffer with volatile cursors
  //        Avoids Queue node allocations, much faster access, and also allows to insert new candidates
  //        right into the buffer, instead of collecting them to temporary lists, as now.


  //================================================================================================
  // MAYBE: errors reporting -- there are a lot of cases there something erroneous could be done async
  //        in a housekeeper thread (or IO-pool). It rises a question about errors reporting: e.g. how
  //        to report IOException from page.flush() if the flush has happened in a IO-pool, triggered
  //        by page allocation pressure, not by any client actions? This is the same question that is
  //        solved with SIGBUS signal in Linux OS file cache impl.
  //        Better solution could be: ring buffer of last 16 exceptions, that could be requested from
  //        any thread, and cleared?


  //================================================================================================
  //Main idea is following: all else equal, the page already loaded is always worth more than the one
  // not loaded. It means that we never unload a page without a reason -- even if the page wasn't used
  // for long. There are 2 reasons for unloading a page: either PagedStorage is closed, or a new page
  // needs to be allocated. But we don't want to look up for page to reclaim through all the cached
  // pages -- this would make new page caching awfully slow. Instead, we scan through the pages
  // regularly, and maintain the list of _candidates_ to reclamation -- i.e. pages, that have the
  // lowest utility, and are not used right now. We eagerly initiate flush for such pages, so nothing
  // would prevent such a page from being unloaded and reclaimed as the need arises.

  private void refillPagesForReclaim(int pagesToCollect) {
    //Now scan all pages and collect candidates for reclamation.
    //Basically, build not-fully-up-to-date index for a query like:
    // `SELECT top <N> FROM pages WHERE usageCount=0 ORDER BY utility DESC`


    CyclicIterator<PagesTable> pagesTablesToScan = threadSafeCopyOfPagesTables();
    int totalPagesCount = Math.max(statistics.totalPagesAllocated() - statistics.totalPagesReclaimed(), 0);

    pagesForReclaimCollector.startCollectingTurn(pagesToCollect, totalPagesCount);
    try {
      int tablesToScan = pagesTablesToScan.size();
      for (int pageTableNo = 0; pageTableNo < tablesToScan; pageTableNo++) {
        PagesTable pageTable = pagesTablesToScan.next();
        AtomicReferenceArray<PageImpl> pages = pageTable.pages();
        int pagesAliveInTable = 0;//count alive entries to shrink the table if there are too few of them
        int pagesCount = pages.length();
        for (int i = 0; i < pagesCount; i++) {
          PageImpl page = pages.get(i);
          if (page == null || page.isTombstone()) {
            continue;
          }

          if (page.isAboutToUnmap()
              && page.usageCount() == 0) {
            //reclaim page right now, no need to enqueue it for later:
            boolean succeed = page.tryMoveTowardsPreTombstone(/*entombYoung: */false);
            if (succeed) {
              unmapPageAndReclaimBuffer(page);
              continue;
            }
          }

          pagesAliveInTable++;

          //Copy 'usefulness' to field accessed by this thread only -- so comparison by this field is
          // stable. Shared field .tokensOfUsefulness could be modified concurrently anytime, hence
          // its use for sorting violates Comparator contract.
          int tokensOfUsefulness = adjustPageUsefulness(page);
          page.updateLocalTokensOfUsefulness(tokensOfUsefulness);

          pagesForReclaimCollector.takePageIfGoodForReclaim(page);
        }

        //shrink table if there are too many tombstones:
        pageTable.shrinkIfNeeded(pagesAliveInTable);

        //if (pagesForReclaimCollector.hasCollectedEnough()) {
        //  break;
        //}
      }
    }
    finally {
      pagesForReclaimCollector.finishCollectingTurn();
    }

    pagesToProbablyReclaim.refill(pagesForReclaimCollector);
  }

  private void releasePagesAllocatedAboveCapacity(int maxPagesToReclaim) {
    //Usually we reclaim pages on the allocatedPageBuffer() allocation path, so the page is
    // evicted only if another page needs its space.
    // To make page allocation path faster, we limit the amount of reclamation work to be done
    // there, and allocate 'above capacity' -- MemoryManager may permit allocations 'slightly
    // above capacity', e.g. from Heap -- and such allocations are used to avoid stalling if not
    // enough pages to reclaim could be found.
    // Here we compensate for those 'above the capacity' allocations: if we allocated above the
    // capacity, we start to reclaim heap pages in housekeeper thread:

    int remainsToReclaim = maxPagesToReclaim;

    //TODO RC: we don't need to scan pagesToProbablyReclaim -- it is CLQ, scanning it is expensive.
    //         better to scan pagesForReclaimCollector lists -- they are array-based, scanning is
    //         very fast, and it is safe since housekeeper thread owns the collections anyway.
    Iterator<PageImpl> it = pagesToProbablyReclaim.iterator();
    while (it.hasNext() && memoryManager.hasOverflow()) {
      PageImpl candidate = it.next();
      if (candidate == null) {
        return;
      }
      ByteBuffer pageBuffer = candidate.pageBufferUnchecked();
      //TODO RC: .isDirect is an abstraction leak -- we must not know that MemoryManager uses heap-buffers
      //         to allocate above capacity. Must be something like memoryManager.isAboveCapacityBuffer(buffer)
      if ((pageBuffer != null && !pageBuffer.isDirect())
          && (candidate.isUsable() || candidate.isAboutToUnmap())
          && candidate.usageCount() == 0) {

        boolean succeed = candidate.tryMoveTowardsPreTombstone(/*entombYoung: */false);
        if (succeed) {
          it.remove();
          remainsToReclaim--;

          unmapPageAndReclaimBuffer(candidate);

          if (remainsToReclaim == 0) {
            return;
          }
        }
      }
    }
  }

  private static int adjustPageUsefulness(@NotNull PageImpl page) {
    int usageCount = page.usageCount();
    if (usageCount > 0) {
      return page.addTokensOfUsefulness(usageCount * TOKENS_PER_USE);
    }
    else {//exponential decay of usefulness:
      return page.decayTokensOfUsefulness(TOKENS_PER_USE - 1, TOKENS_PER_USE);
    }
  }

  private int cleanClosedStoragesAndReclaimPages(int maxStoragesToProcess) {
    int successfullyCleaned = 0;
    for (int i = 0; i < maxStoragesToProcess; i++) {
      Command command = commandsQueue.poll();
      if (command == null) {
        break;
      }

      if (command instanceof PostCloseStorageCleanupCommand) {
        final PostCloseStorageCleanupCommand closeStorageCommand = (PostCloseStorageCleanupCommand)command;
        final PagedFileStorageWithRWLockedPageContent storage = closeStorageCommand.storageToClose;
        final CompletableFuture<?> futureToFinalize = closeStorageCommand.onFinish;
        if (!storage.isClosed()) {
          AssertionError error = new AssertionError(
            "Code bug: storage " + storage + " must be closed before PostCloseStorageCleanupCommand is queued");
          futureToFinalize.completeExceptionally(error);
          throw error;
        }

        PagesTable pagesTable = storage.pages();
        // RC: Actually, we don't need to _reclaim_ pages of .close()-ed storage -- we need to .flush()
        //     them, but the pages themselves are owned by cache, not storage. So it may be
        //     reasonable to just leave pages there they are, until they are either reclaimed by
        //     a regular reclamation path (because their utility is completely decayed under no
        //     use), or re-used by a new storage opened for the same file. IMHO, for our use-cases,
        //     the ability to re-use pages for re-opened storages is not very important, but the
        //     opportunity to simplify .close() codepath may be worth it.

        //TODO RC: catch UncheckedIOException from page.flush() calls (in entombPageAndGetPageBuffer),
        //         and collect them. Do limited retries: count unsuccessful page.flush(), and give up after
        //         N tries -- remove task from queue, rise an exception leading to app restart
        //         request (not worth to allow user continue working if we know work can't be
        //         saved)
        boolean somePagesStillInUse = tryToReclaimAll(pagesTable);
        if (!somePagesStillInUse) {
          Path file = storage.getFile();
          try {
            //TODO RC: this better to be responsibility of the storage (why cache should even know
            //         about channels cache?)
            PageCacheUtils.CHANNELS_CACHE.closeChannel(file);
          }
          catch (Throwable t) {
            LOG.error("Can't close channel for " + file, t);
            futureToFinalize.completeExceptionally(t);
          }
          successfullyCleaned++;
          synchronized (pagesPerFile) {
            Path absolutePath = storage.getFile().toAbsolutePath();
            PagesTable removed = pagesPerFile.remove(absolutePath);
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

    return successfullyCleaned;
  }

  private CyclicIterator<PagesTable> threadSafeCopyOfPagesTables() {
    synchronized (pagesPerFile) {
      pageTableCyclicIterator.update(pagesPerFile.values());
      return pageTableCyclicIterator;
    }
  }


  /**
   * Method tries to unmap and reclaim all pages of a given PagesTable. It is basically for a
   * 'finalize & cleanup' after page storage owning pagesTable is closed -- i.e. it is known
   * there will be no new clients of storage pages, and current clients are about to release
   * their pages soon.
   * <p/>
   * Since some pages could be still in use, there is no guarantee the method could reclaim
   * all pages right now -- hence, the method is designed to be called repeatedly, until it
   * returns true, which means all pages are reclaimed.
   * <p/>
   * Method moves all pages to {@link PageImpl#STATE_ABOUT_TO_UNMAP} state, and if page has
   * usageCount=0 -> reclaim it immediately. Pages with usageCount > 0 are not reclaimed, and
   * the method returns false if there is at least one such a page.
   *
   * @return true if all pages are reclaimed, false if there are some pages that are still in
   * use and can't be reclaimed right now -- so method should be called again, later
   */
  boolean tryToReclaimAll(@NotNull PagesTable pagesTable) {
    pagesTable.pagesLock().lock();      //RC: Do we need a lock here really?
    try {
      AtomicReferenceArray<PageImpl> pages = pagesTable.pages();
      boolean somePagesStillInUse = false;
      for (int i = 0; i < pages.length(); i++) {
        PageImpl page = pages.get(i);
        if (page == null || page.isTombstone()) {
          continue;
        }

        boolean succeed = page.tryMoveTowardsPreTombstone(/*entombYoung: */true);
        if (succeed) {
          if (page.pageBufferUnchecked() != null) {
            unmapPageAndReclaimBuffer(page);
          }
          else {//If we just entomb NOT_READY_YET page => it has .data=null
            page.entomb();
          }
        }

        //if !TOMBSTONE => wait till next time, hope the page will be released eventually
        somePagesStillInUse |= !page.isTombstone();
      }
      return somePagesStillInUse;
    }
    finally {
      pagesTable.pagesLock().unlock();
    }
  }

  /**
   * Reclaims the page: flushes page content if needed, and release page buffer.
   * Page must be in state=PRE_TOMBSTONE (throws AssertionError otherwise)
   */
  void unmapPageAndReclaimBuffer(@NotNull PageImpl pageToReclaim) {
    ByteBuffer pageBuffer = entombPageAndGetPageBuffer(pageToReclaim);

    reclaimPageBuffer(pageToReclaim.pageSize(), pageBuffer);
  }

  /**
   * Release the buffer to the pool if it is a direct buffer, or just release it, if it is a heap buffer.
   * Adjust statistical counters accordingly.
   */
  void reclaimPageBuffer(int pageSize,
                         @NotNull ByteBuffer pageBuffer) {
    memoryManager.releaseBuffer(pageSize, pageBuffer);
  }

  void waitForHousekeepingTurn(int maxWaitMs){
    pagesToProbablyReclaim.waitForRefill(maxWaitMs);
  }

  private static @NotNull ByteBuffer entombPageAndGetPageBuffer(@NotNull PageImpl pageToUnmap) {
    if (!pageToUnmap.isPreTombstone()) {
      throw new AssertionError("Bug: page must be PRE_TOMBSTONE: " + pageToUnmap);
    }
    if (pageToUnmap.isDirty()) {
      //MAYBE RC: flush() is better be off-loaded to IO thread pool, instead of slowing down housekeeper
      //         thread?
      try {
        pageToUnmap.flush();
      }
      catch (IOException e) {
        throw new UncheckedIOException("Can't flush page: " + pageToUnmap, e);
      }
    }
    ByteBuffer pageBuffer = pageToUnmap.detachTombstoneBuffer();
    pageToUnmap.entomb();
    return pageBuffer;
  }

  @NotNull ByteBuffer allocatePageBuffer(int bufferSize) {
    checkNotClosed();

    for (int attempt = 0; ; attempt++) {
      ByteBuffer allocatedBuffer = memoryManager.tryAllocate(bufferSize, /* aboveCapacity: */ false);
      if (allocatedBuffer != null) {
        return allocatedBuffer;
      }

      ByteBuffer reclaimedBuffer = tryReclaimPageOfSize(bufferSize, MAX_PAGES_TO_TRY_RECLAIM_ON_ALLOCATION);
      if (reclaimedBuffer != null) {
        statistics.pageReclaimedByHandover(bufferSize, reclaimedBuffer.isDirect());
        return reclaimedBuffer;
      }

      ByteBuffer aboveCapacityBuffer = memoryManager.tryAllocate(bufferSize, /* aboveCapacity: */ true);
      if (aboveCapacityBuffer != null) {
        return aboveCapacityBuffer;
      }

      //Wakeup housekeeper, and wait for it to collect more pages
      statistics.pageAllocationWaited();
      wakeupHousekeeper();
      pagesToProbablyReclaim.waitForRefill(1 /*ms*/);
      //MAYBE RC: Waiting for housekeeper to collect new portion of pages for us -- is basically a
      // backpressure. We slow down the current thread so the housekeeper could keep up.
      // We could avoid this waiting by assisting housekeeper instead -- i.e. we could scan through
      // PageTables looking for the first page ready to reclaim.
      //
      // So far I think that would be an overkill: if some thread(s) requests pages so greedy that
      // housekeeper can't keep up -- it is better to slow that thread down with backpressure than
      // to allow the greedy thread to churn pages wildly.
      // Backpressure is ~1ms (housekeeper turn period), so it unlikely creates freezes noticeable
      // to user, but it makes Cache more fair/cooperative, by prohibiting single thread to exhaust
      // cache throughput.
      // Time will tell is it correct reasoning.
    }
  }

  private void wakeupHousekeeper() {
    synchronized (housekeeperSleepLock) {
      //noinspection CallToNotifyInsteadOfNotifyAll
      housekeeperSleepLock.notify();
    }
  }

  /**
   * Method scans through {@link #pagesToProbablyReclaim} and reclaims pages until either:
   * 1. Page with buffer.capacity() == bufferSize is reclaimed
   * 2. Cache has enough free capacity for allocating additional bufferSize.
   * 3. maxPagesToTry were examined, or queue is empty, and none of the above is true.
   * In 1-st case, method returns buffer just reclaimed. In 2nd and 3rd cases method returns null,
   * and it is left to the caller to check cache capacity and find out which case it was.
   */
  private @Nullable ByteBuffer tryReclaimPageOfSize(int bufferSize,
                                                    int maxPagesToTry) {
    int dirtyPagesSkipped = 0;
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int pagesTried = 0;
         pagesTried < maxPagesToTry && !memoryManager.hasFreeNativeCapacity(bufferSize);
         pagesTried++) {
      PageImpl candidateToReclaim = pagesToProbablyReclaim.poll();
      if (candidateToReclaim == null) {
        return null;//nothing more to reclaim
      }
      if (candidateToReclaim.usageCount() > 0) {
        continue;//page is in use by somebody (don't return it to the reclaim queue)
      }
      if (candidateToReclaim.isDirty()) {
        //We _prefer_ to not reclaim dirty pages here, on page allocation path, since .flush()
        //  creates unpredictable delays -- hence we _prefer_ to skip dirty pages, looking for
        //  non-dirty ones.
        //  But we can't skip all dirty pages, since it could be _no_ non-dirty pages
        //  available -- and scanning all pagesToProbablyReclaim to find it out is also a
        //  high cost. Hence, we skip dirty pages probabilistically: we accept N-th dirty page
        //  with probability N/maxPagesToTry (otherwise return dirty page to the queue end)
        if (dirtyPagesSkipped <= rnd.nextInt(maxPagesToTry)) {
          dirtyPagesSkipped++;
          pagesToProbablyReclaim.pushBack(candidateToReclaim);
          //MAYBE RC: also queue async .flush() for the page?
          continue;
        }
      }

      boolean succeed = candidateToReclaim.tryMoveTowardsPreTombstone(/*entombYoung: */false);
      if (succeed) {
        // > 1 thread could try to reclaim the page, but we win the race:
        ByteBuffer reclaimedBuffer = entombPageAndGetPageBuffer(candidateToReclaim);
        //'handoff': reuse reclaimed buffer immediately, if it's capacity is enough
        // (but not too much so we don't waste too much memory using huge buffer for small page)
        if (bufferSize <= reclaimedBuffer.capacity() && reclaimedBuffer.capacity() <= 2 * bufferSize) {
          reclaimedBuffer.clear().limit(bufferSize);
          return reclaimedBuffer;
        }
        else {
          reclaimPageBuffer(candidateToReclaim.pageSize(), reclaimedBuffer);
        }
      }
    }
    return null;
  }

  private void checkNotClosed() throws IllegalStateException {
    if (state == STATE_CLOSED) {
      throw new IllegalStateException("Cache is already closed");
    }
  }

  /**
   * Storages could 'ask' FilePageCache to do something asynchronously. This is the base class
   * for such requests.
   * RC: Class is empty, since now there is only one impl of this class, and I'm not yet sure
   * which API is worth to have in a base class.
   */
  protected abstract static class Command {
  }

  /**
   * Command issued by {@link PagedFileStorageWithRWLockedPageContent} on a close -- request FilePageCache to clean
   * up all used data of the storage.
   */
  protected static class PostCloseStorageCleanupCommand extends Command {
    private final PagedFileStorageWithRWLockedPageContent storageToClose;
    private final CompletableFuture<?> onFinish;

    protected PostCloseStorageCleanupCommand(final @NotNull PagedFileStorageWithRWLockedPageContent storageToClose,
                                             final @NotNull CompletableFuture<Object> onFinish) {
      this.storageToClose = storageToClose;
      this.onFinish = onFinish;
    }
  }

  /**
   * Receives pages during page scanning, and selects those that are best suited for reclaiming
   * (i.e. least useful by some measure). So it is basically F[ page* -> pages good for reclaim ]
   * <p>
   * Internally, it maintains a reclamation threshold and other parameters.
   */
  private static class PagesForReclaimCollector {

    private static final Comparator<PageImpl> BY_USEFULNESS = comparing(page -> page.localTokensOfUsefulness());

    /**
     * Keeps and updates a 'low-usefulness' threshold: i.e. which value of page.tokensOfUsefulness
     * considered 'low', so the pages with usefulness below it are considered candidates for reclamation.
     * By default, 'low-usefulness' is the bottom 10% of all pages, but this could be increased if
     * page allocation/reclamation pressure is high.
     */
    private final FrugalQuantileEstimator lowUsefulnessThresholdEstimator;
    private final int minPercentOfPagesToPrepareForReclaim;
    private final int maxPercentOfPagesToPrepareForReclaim;

    private @NotNull List<PageImpl> pagesForReclaimNonDirty = Collections.emptyList();
    private @NotNull List<PageImpl> pagesForReclaimDirty = Collections.emptyList();

    private int maxPagesToCollect;

    public PagesForReclaimCollector(int minPercentOfPagesToPrepareForReclaim,
                                    int maxPercentOfPagesToPrepareForReclaim) {
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

    public void startCollectingTurn(int pagesToCollect,
                                    int totalPagesCount) {
      if (pagesToCollect <= 0) {
        throw new IllegalArgumentException("pagesToCollect(=" + pagesToCollect + ") must be > 0");
      }
      if (totalPagesCount < 0) {
        throw new IllegalArgumentException("totalPagesCount(=" + totalPagesCount + ") must be >= 0");
      }
      this.maxPagesToCollect = Math.max(
        pagesToCollect,
        totalPagesCount * lowUsefulnessThresholdEstimator.percentileToEstimate() / 100 + 1
      );

      int listsSizeEstimation = Math.max(pagesToCollect / 2, 32);
      pagesForReclaimDirty = new ArrayList<>(listsSizeEstimation);
      pagesForReclaimNonDirty = new ArrayList<>(listsSizeEstimation);
    }

    public void finishCollectingTurn() {
      pagesForReclaimDirty.sort(BY_USEFULNESS);
      pagesForReclaimNonDirty.sort(BY_USEFULNESS);
    }

    public List<PageImpl> pagesForReclaimNonDirty() { return pagesForReclaimNonDirty; }

    public List<PageImpl> pagesForReclaimDirty() { return pagesForReclaimDirty; }

    public int totalPagesPreparedToReclaim() {
      return pagesForReclaimDirty.size() + pagesForReclaimNonDirty.size();
    }

    public void collectMoreAggressively() {
      int currentPercentage = lowUsefulnessThresholdEstimator.percentileToEstimate();
      lowUsefulnessThresholdEstimator.updateTargetPercentile(
        clamp(currentPercentage + 1, minPercentOfPagesToPrepareForReclaim, maxPercentOfPagesToPrepareForReclaim)
      );
    }

    public void collectLessAggressively() {
      int currentPercentage = lowUsefulnessThresholdEstimator.percentileToEstimate();
      lowUsefulnessThresholdEstimator.updateTargetPercentile(
        clamp(currentPercentage - 1, minPercentOfPagesToPrepareForReclaim, maxPercentOfPagesToPrepareForReclaim)
      );
    }

    /**
     * Try to ensure cleanPagesForReclaim/totalPagesForReclaim >= fractionOfCleanPagesToAim
     * If there are fewer clean pages than requested -> try to flush some dirty pages, until
     * the fractionOfCleanPagesToAim is satisfied.
     *
     * @return how many pages were flushed as a result
     */
    public int ensureEnoughCleanPagesToReclaim(double fractionOfCleanPagesToAim) {
      int totalPagesToReclaim = pagesForReclaimDirty.size() + pagesForReclaimNonDirty.size();
      int dirtyPagesTargetCount = (int)((1 - fractionOfCleanPagesToAim) * totalPagesToReclaim);
      int pagesToFlush = pagesForReclaimDirty.size() - dirtyPagesTargetCount;
      int actuallyFlushed = 0;
      for (int i = 0; i < pagesToFlush; i++) {
        PageImpl page = pagesForReclaimDirty.get(i);
        if (page.isTombstone()) {
          continue;
        }
        try {
          //TODO RC: .tryFlush() prevents housekeeper thread from stalling on the page lock -- but the
          //         thread could still get stuck on actual flush IO. Ideally all IO should be offloaded
          //         from the housekeeper thread to an IO pool.
          if (page.tryFlush()) {
            actuallyFlushed++; //not strictly true, since actual flush could be short-circuit, but...
          }//MAYBE RC: else -> remove page from candidates for reclamation, since it is IN USE now?
        }
        catch (IOException e) {
          LOG.warn("Can't flush page " + page, e);
        }
      }
      return actuallyFlushed;
    }


    public boolean hasCollectedEnough() {
      return totalPagesPreparedToReclaim() >= maxPagesToCollect;
    }

    public boolean takePageIfGoodForReclaim(@NotNull PageImpl page) {
      int tokensOfUsefulness = page.tokensOfUsefulness();
      double lowUsefulnessThreshold = lowUsefulnessThresholdEstimator.updateEstimation(tokensOfUsefulness);

      //MAYBE RC: separate scanning to gauge utility stats -- from scanning to collect pages for reclamation?
      if (!hasCollectedEnough()) {
        if (isGoodForReclaim(page, lowUsefulnessThreshold)) {
          addCandidateForReclaim(page);
          return true;
        }
      }
      return false;
    }

    public boolean isGoodForReclaim(@NotNull PageImpl page) {
      return isGoodForReclaim(page, lowUsefulnessThresholdEstimator.currentEstimation());
    }

    public boolean isGoodForReclaim(@NotNull PageImpl page,
                                    double lowUsefulnessThreshold) {
      int tokensOfUsefulness = page.tokensOfUsefulness();
      return page.isUsable()
             && page.usageCount() == 0
             && tokensOfUsefulness <= lowUsefulnessThreshold;
    }

    private void addCandidateForReclaim(@NotNull PageImpl page) {
      if (page.isDirty()) {
        pagesForReclaimDirty.add(page);
      }
      else {
        pagesForReclaimNonDirty.add(page);
      }
    }

    @Override
    public String toString() {
      return "PagesForReclaimCollector["
             + pagesForReclaimDirty.size() + " dirty/" + pagesForReclaimNonDirty.size() + " non-dirty, "
             + "low usefulness <= " + lowUsefulnessThresholdEstimator.currentEstimation()
             + " (" + lowUsefulnessThresholdEstimator.percentileToEstimate() + "%)"
             + "]";
    }
  }

  /** Keeps pages that are good candidates for reclaim */
  private static class PagesToReclaim {

    //TODO RC: CLQueue is quite expensive for use here -- node allocations, O(N) size, and so on -- while we
    //         don't benefit from the strict order it provides.
    //         Fixed-size ring buffer with volatile head-tail cursors is cheaper, while serves same purpose.
    //         It also allows to insert new candidates right into the buffer, instead of collecting them to
    //         temporary lists, as we now in PagesForReclaimCollector

    private volatile ConcurrentLinkedQueue<PageImpl> pagesToProbablyReclaimQueue = new ConcurrentLinkedQueue<>();
    /** CLQ.size is O(N), so cache the size here */
    private final AtomicInteger pagesInQueue = new AtomicInteger(0);

    /**
     * Lock object is used to wait for pagesToProbablyReclaimQueue to refill,
     * and to notify the waiters about pages refilled
     */
    private final Object refillSignalLock = new Object();

    private long lastRefillAtNs = 0;


    public int size() {
      //FiXME RC: CLQ.size is O(N), but current attempt to fix it with counter leads to freezes
      //          due to out-of-sync counter & queue
      return pagesToProbablyReclaimQueue.size();
      //return pagesInQueue.get();
    }

    public boolean isOlderThen(long nowNs,
                               long thresholdNs) {
      return (nowNs - thresholdNs > lastRefillAtNs);
    }

    public void refill(@NotNull PagesForReclaimCollector pagesCollector) {
      ConcurrentLinkedQueue<PageImpl> pagesForReclaim = new ConcurrentLinkedQueue<>();
      List<PageImpl> forReclaimDirty = pagesCollector.pagesForReclaimDirty();
      List<PageImpl> forReclaimNonDirty = pagesCollector.pagesForReclaimNonDirty();
      pagesForReclaim.addAll(forReclaimDirty);
      pagesForReclaim.addAll(forReclaimNonDirty);

      this.pagesToProbablyReclaimQueue = pagesForReclaim;
      this.pagesInQueue.addAndGet(forReclaimDirty.size() + forReclaimNonDirty.size());

      lastRefillAtNs = System.nanoTime();
      notifyAboutRefill();
    }

    public @Nullable PageImpl poll() {
      PageImpl page = pagesToProbablyReclaimQueue.poll();
      if (page != null) {
        pagesInQueue.decrementAndGet();
      }
      return page;
    }

    public void pushBack(@NotNull PageImpl page) {
      pagesToProbablyReclaimQueue.offer(page);
      pagesInQueue.incrementAndGet();
    }

    public Iterator<PageImpl> iterator() {
      Iterator<PageImpl> it = pagesToProbablyReclaimQueue.iterator();
      return new Iterator<PageImpl>() {
        @Override
        public boolean hasNext() {
          return it.hasNext();
        }

        @Override
        public PageImpl next() {
          return it.next();
        }

        @Override
        public void remove() {
          it.remove();
          pagesInQueue.decrementAndGet();
        }
      };
    }

    private void notifyAboutRefill() {
      synchronized (refillSignalLock) {
        refillSignalLock.notifyAll();
      }
    }

    private void waitForRefill(int maxWaitMs) {
      synchronized (refillSignalLock) {
        try {
          //noinspection WaitNotInLoop
          refillSignalLock.wait(maxWaitMs);
        }
        catch (InterruptedException ignored) {
        }
      }
    }

    @Override
    public String toString() {
      return "PagesToReclaim[" + pagesInQueue + " in queue]";
    }
  }

  /**
   * Cyclically iterates through the list of items, while the items could be changed on the go.
   * Iteration order is not strictly defined, but it tries to not list the item a second time until
   * all other items are listed.
   */
  private static class CyclicIterator<T> implements Iterator<T> {

    private Collection<T> currentItems;
    private @NotNull ArrayDeque<T> processedItems = new ArrayDeque<>();
    private @NotNull ArrayDeque<T> unprocessedItems = new ArrayDeque<>();

    public void update(@NotNull Collection<T> items) {
      if (currentItems != null
          && currentItems.containsAll(items)
          && currentItems.size() == items.size()) {
        //currentItems unchanged => no need to re-evaluate processed & unprocessed
        return;
      }
      currentItems = CollectionFactory.createSmallMemoryFootprintSet(items);

      processedItems = new ArrayDeque<>(intersection(processedItems, currentItems));
      unprocessedItems = new ArrayDeque<>(subtract(currentItems, processedItems));
    }

    @Override
    public boolean hasNext() {
      if (currentItems == null) {
        throw new IllegalStateException(".update() must be called first");
      }
      return !unprocessedItems.isEmpty() || !processedItems.isEmpty();
    }

    @Override
    public T next() {
      if (!hasNext()) {
        throw new NoSuchElementException(
          "Nothing to offer: " +
          "unprocessed=" + unprocessedItems + ", processed=" + processedItems + ", current=" + currentItems
        );
      }
      if (unprocessedItems.isEmpty()) {
        unprocessedItems = processedItems;
        processedItems = new ArrayDeque<>();
      }
      T item = unprocessedItems.poll();
      processedItems.add(item);
      return item;
    }

    public int size() {
      return currentItems.size();
    }
  }

  private class RateController {
    private long totalPagesAllocatedTurnBefore = 0L;
    private long totalPagesWaitedTurnBefore = 0L;

    private final ConfinedIntValue safetyMarginFactor = new ConfinedIntValue(12, 12, 36);

    public int predictPagesDemandForNextTurn() {
      //We need a time series forecasting here:
      // 1) Value we're trying to predict is statistics.totalPagesAllocated() increase on next turn.
      // 2) We don't need a precise forecast: it is fine to be systematically biased 'above' -- we
      //    usually over-provision pages for reclaim anyway, so being biased 'above' doesn't change
      //    much, as long as we're not order of magnitude off.
      // 3) We should try hard to NOT systematically forecast 'below', since this leads to worker threads
      //    stalling -- we could use statistics.totalPageAllocationsWaited() as a feedback to auto-tune
      //    forecast parameters to reach that goal.

      //Right now we use very primitive forecasting: forecast is just current value is multiplied by
      //  some 'safety margin factor'. Safety margin factor is a value in range [10/10...36/10], and we
      //  double it each time some there are 'waits' during turn before, and decrement it each time
      //  there were no waits -- so it is a simple feedback loop 'multiplicative-increase/additive-decrease'.

      long totalPagesAllocatedNow = statistics.totalPagesAllocated();
      int totalPagesWaitedNow = statistics.totalPageAllocationsWaited();

      int pagesAllocatedInTurn = Math.toIntExact(totalPagesAllocatedNow - totalPagesAllocatedTurnBefore);
      int pagesWaitedInTurn = Math.toIntExact(totalPagesWaitedNow - totalPagesWaitedTurnBefore);

      totalPagesAllocatedTurnBefore = totalPagesAllocatedNow;
      totalPagesWaitedTurnBefore = totalPagesWaitedNow;

      if (pagesWaitedInTurn > 0) {
        safetyMarginFactor.update(safetyMarginFactor.value() * 2);
      }
      else {
        safetyMarginFactor.dec();
      }

      return Math.max(
        pagesAllocatedInTurn * safetyMarginFactor.value() / 10  + 1,
        1
      );
    }
  }
}
