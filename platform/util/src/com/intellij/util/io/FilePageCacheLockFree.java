// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.pagecache.FilePageCacheStatistics;
import com.intellij.util.io.pagecache.impl.FrugalQuantileEstimator;
import com.intellij.util.io.pagecache.impl.PageImpl;
import com.intellij.util.io.pagecache.impl.PagesTable;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;

/**
 * Maintains 'pages' of data (in the form of {@linkplain PageImpl}), from file storages {@linkplain PagedFileStorageLockFree}.
 * <br>
 * This class is not a file cache per se (this is {@linkplain PagesTable}) but mostly a cache housekeeper:
 * it does background jobs to keep pages cache at bay. I.e. it maintains a pool of buffers, limits the
 * total size of buffers in use, does async flushing, flushes and re-uses the least used pages, and so on.
 * <br>
 * Page cache maintains limit on number of pages cached: {@linkplain #cacheCapacityBytes}. As the limit is
 * reached, oldest and/or least used pages are evicted from cache (and flushed to disk). Pages also evicted
 * with their owner {@linkplain PagedFileStorageLockFree} re-registration {@linkplain #enqueueStoragePagesClosing(PagedFileStorageLockFree, CompletableFuture)}
 * <p>
 */
@ApiStatus.Internal
public final class FilePageCacheLockFree implements AutoCloseable {
  private static final Logger LOG = Logger.getInstance(FilePageCacheLockFree.class);

  private static final int MAX_PAGES_TO_RECLAIM_AT_ONCE = 5;
  /** Initial size of page table hashmap in {@linkplain PagesTable} */
  private static final int INITIAL_PAGES_TABLE_SIZE = 1 << 5;

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


  /* ================= instance fields ============================================================== */

  private final long cacheCapacityBytes;

  /** Total bytes cached as DirectByteBuffers */
  private final AtomicLong totalNativeBytesCached = new AtomicLong(0);
  /** Total bytes cached as HeapByteBuffers */
  private final AtomicLong totalHeapBytesCached = new AtomicLong(0);

  //@GuardedBy("pagesPerStorage")
  private final Object2ObjectOpenHashMap<Path, PagesTable> pagesPerFile = new Object2ObjectOpenHashMap<>();

  /**
   * Queue of pages that are likely the best suited to reclaim -- i.e. to unmap, and re-use their
   * buffer for another page needs to be loaded. To fill the queue housekeeper thread periodically
   * scans all the pages and selects the pages that are not in use right now, and used the least
   * recently.
   * Bear in mind that page state changes asynchronously, so this queue is not exactly up-to-date.
   * I.e. pages in this queue are just 'likely' to be the ones ready to reclaim -- it is possible
   * that somebody starts using a page after it was put in the queue, and hence the page in queue
   * becomes not eligible to reclaim anymore. So each page must be carefully inspected before
   * reclaiming it.
   */
  private volatile ConcurrentLinkedQueue<PageImpl> pagesToProbablyReclaimQueue = new ConcurrentLinkedQueue<>();

  private final ConcurrentLinkedQueue<Command> commandsQueue = new ConcurrentLinkedQueue<>();

  private final PagesForReclaimCollector pagesForReclaimCollector;

  private final Thread housekeeperThread;

  /** PageCache lifecycle state */
  private volatile int state = STATE_NOT_STARTED;

  private final FilePageCacheStatistics statistics = new FilePageCacheStatistics();


  public FilePageCacheLockFree(final long cacheCapacityBytes) {
    this(cacheCapacityBytes, r -> new Thread(r, "FilePageCache housekeeper"));
  }

  public FilePageCacheLockFree(final long cacheCapacityBytes,
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
    state = STATE_WAITING_FIRST_STORAGE_REGISTRATION;
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

  protected Future<?> enqueueStoragePagesClosing(final @NotNull PagedFileStorageLockFree storage,
                                                 final @NotNull CompletableFuture<Object> finish) {
    checkNotClosed();
    final CloseStorageCommand task = new CloseStorageCommand(storage, finish);
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
    return statistics;
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

          statistics.cacheMaintenanceTurnDone();
        }
        else {
          statistics.cacheMaintenanceTurnSkipped();
        }

        //assess allocation pressure and adjust our efforts
        if (pagesRemainedToReclaim > pagesPreparedToReclaim / 2) {
          //allocation pressure low: could collect less and sleep more
          pagesForReclaimCollector.collectLessAggressively();
          Thread.sleep(1);
        }
        else if (pagesRemainedToReclaim > 0) {
          //allocation pressure high, but we ~catch up with it: just yield
          Thread.yield();
        }
        else {
          //allocation pressure is so high we don't catch up with it:
          // no time to wait
          // need to collect more pages-to-reclaim
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

  //TODO RC:
  //     1. Trace allocation pressure: (pagesToReclaim (before) - pagesToReclaimQueue.size), and use
  //        QuantilesEstimator(80-90%) to estimate percentage of pages to prepare to reclaim.
  //        Also use it to estimate how much 'eager flushes' needs to be done.
  //     2. In .allocatePageBuffer(): trace counts of .flush() invoked
  //     3. If a lot of .flush() invoked -> increase probability of 'eager flushes' in (2)
  //     4. 'Buffer bargaining': if in allocatePageBuffer() we reclaimed a page with
  //         (buffer.capacity() == bufferSize) => use the buffer directly, instead of returning
  //         it to the pool and than query it from there again.


  //================================================================================================
  // IDEA: errors reporting -- there are a lot of cases there something errorness could be done async
  //       in a housekeeper thread (or IO-pool). But it rises a question about errors reporting:
  //       e.g. how to report IOException during page.flush() if the flush has happened in a IO-pool,
  //       triggered by page allocation pressure, not by any client actions? This is the same question
  //       that is solved with SIGBUS signal in Linux OS file cache impl.
  //       Better solution could be: ring buffer of last 16 exceptions, that could be requested from
  //       any thread, and cleared.

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
    final int reclaimed = cleanClosedStoragesAndReclaimPages(/* max per turn: */ 1);
    statistics.closedStoragesReclaimed(reclaimed);


    //Now scan all pages and collect candidates for reclamation: basically, build not-fully-up-to-date
    // index for a query like
    // `SELECT top <N> FROM pages WHERE usageCount=0 ORDER BY utility DESC`

    final Map<Path, PagesTable> pagesPerStorage = threadSafeCopyOfPagesPerStorage();
    pagesForReclaimCollector.startCollectingTurn();
    try {
      //TODO RC: organize all tables pages in Iterable<Page>
      final Collection<PagesTable> pagesTables = pagesPerStorage.values();
      for (PagesTable pagesTable : pagesTables) {
        final AtomicReferenceArray<PageImpl> pages = pagesTable.pages();
        int pagesAliveInTable = 0;//will shrink table if too much TOMBSTONEs
        for (int i = 0; i < pages.length(); i++) {
          final PageImpl page = pages.get(i);
          if (page == null || page.isTombstone()) {
            continue;
          }

          pagesAliveInTable++;

          if (page.isAboutToUnmap() && page.usageCount() == 0) {
            //reclaim page right now, no need to enqueue it for later:
            final boolean succeed = page.tryMoveTowardsPreTombstone(/*entombYoung: */false);
            if (succeed) {
              unmapPageAndReclaimBuffer(page);
              continue;
            }
          }

          //Copy usefulness to field accessed by this thread only -- so comparison by this field is
          // stable. Shared field .tokensOfUsefulness could be modified concurrently anytime, hence
          // its use for sorting violates Comparator contract.
          final int tokensOfUsefulness = adjustPageUsefulness(page);
          page.updateLocalTokensOfUsefulness(tokensOfUsefulness);

          pagesForReclaimCollector.checkPageGoodForReclaim(page);
        }

        //shrink table if too many tombstones:
        pagesTable.shrinkIfNeeded(pagesAliveInTable);
      }
    }
    finally {
      pagesForReclaimCollector.finishCollectingTurn();
    }

    pagesToProbablyReclaimQueue = pagesForReclaimCollector.pagesForReclaimAsQueue();

    //if <50% of pages collected to reclaim are clean -> try to eagerly flush some dirty pages:
    final int pagesFlushed = pagesForReclaimCollector.ensureEnoughCleanPagesToReclaim(0.5);

    //Usually we reclaim pages from the new page allocation path, so the page is evicted only
    // if another page needs its space. To make page allocation path faster, though, we limit the
    // amount of reclamation work to be done there, and allow to allocate 'above the capacity', from
    // Heap -- if not enough pages to reclaim could be found. Here we compensate for those 'above
    // the capacity' allocations: if we allocated too much above the capacity, we start to reclaim
    // pages in housekeeper thread:
    int remainsToReclaim = 10;
    while (totalNativeBytesCached.get() + totalHeapBytesCached.get() > cacheCapacityBytes
           && remainsToReclaim > 0) {
      final PageImpl candidate = pagesToProbablyReclaimQueue.poll();
      if (candidate == null) {
        break;
      }
      if (candidate.isUsable() && candidate.usageCount() == 0) {
        final ByteBuffer data = candidate.data();
        if (data != null && !data.isDirect()) {
          final boolean succeed = candidate.tryMoveTowardsPreTombstone(/*entombYoung: */false);
          if (succeed) {
            unmapPageAndReclaimBuffer(candidate);
            remainsToReclaim--;
          }
        }
      }
    }
  }

  private static int adjustPageUsefulness(final @NotNull PageImpl page) {
    final int usageCount = page.usageCount();
    if (usageCount > 0) {
      return page.addTokensOfUsefulness(usageCount * TOKENS_PER_USE);
    }
    else {//exponential decay of usefulness:
      return page.decayTokensOfUsefulness(TOKENS_PER_USE - 1, TOKENS_PER_USE);
    }
  }

  private int cleanClosedStoragesAndReclaimPages(final int maxStoragesToProcess) {
    int successfullyCleaned = 0;
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
          catch (Throwable t) {
            LOG.error("Can't close channel for " + file, t);
            futureToFinalize.completeExceptionally(t);
          }
          successfullyCleaned++;
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

    return successfullyCleaned;
  }

  @NotNull
  private Map<Path, PagesTable> threadSafeCopyOfPagesPerStorage() {
    synchronized (pagesPerFile) {
      return new HashMap<>(pagesPerFile);
    }
  }


  /**
   * Method tries to unmap and reclaim all pages of a given PagesTable. It moves all pages
   * to {@link PageImpl#STATE_ABOUT_TO_UNMAP} state, and if page has usageCount=0 -> reclaim it immediately.
   * Pages with usageCount > 0 are not reclaimed, and method returns false if there is at least one
   * such a page. Method is designed to be called repeatedly, until all pages are reclaimed.
   */
  protected boolean tryToReclaimAll(final @NotNull PagesTable pagesTable) {
    pagesTable.pagesLock().writeLock().lock();
    try {
      final AtomicReferenceArray<PageImpl> pages = pagesTable.pages();
      boolean somePagesStillInUse = false;
      for (int i = 0; i < pages.length(); i++) {
        final PageImpl page = pages.get(i);
        if (page == null || page.isTombstone()) {
          continue;
        }

        if (!page.isNotReadyYet()) {
          //Simple cases: USABLE or ABOUT_TO_UNMAP
          final boolean succeed = page.tryMoveTowardsPreTombstone(/*entombYoung: */false);
          if (succeed) {
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
          //          this branch reclaims such a page, while without it the page remains leaking.
          //          Maybe I'll reconsider usefulness of all this in the future.

          //Acquire page.writeLock to stop page from being promoted to USABLE (if it hasn't been yet):
          if (page.pageLock().writeLock().tryLock()) {
            try {
              final boolean succeed = page.tryMoveTowardsPreTombstone(/*entombYoung: */ true);
              if (succeed) {
                //If we just entomb NOT_READY_YET page => it has .data=null
                //   but page could be already promoted to USABLE, in which case .data != null, and
                //   should be reclaimed:
                if (page.data() != null) {
                  unmapPageAndReclaimBuffer(page);
                }
                else {
                  page.entomb();
                }
              }
            }
            finally {
              page.pageLock().writeLock().unlock();
            }
          }
          else {
            //We've lost the race: somebody locked the page for read/write, hence page (likely)
            // was promoted to USABLE and acquired (usageCount>0).
            // => try at least to mark the page as ABOUT_TO_UNMAP, to stop additional clients
            // => wait till next time, hope the page will be released then
            //MAYBE RC: seems like this is neat-peeking -- case is rare, and process it here adds almost
            //          nothing, but complicates code. Better leave such pages until next turn?
            final boolean succeed = page.tryMoveTowardsPreTombstone(/*entombYoung: */false);
            if (succeed) {
              unmapPageAndReclaimBuffer(page);
            }
          }
        }

        //if !TOMBSTONE => wait till next time, hope the page will be released eventually
        somePagesStillInUse |= !page.isTombstone();
      }
      return somePagesStillInUse;
    }
    finally {
      pagesTable.pagesLock().writeLock().unlock();
    }
  }

  /**
   * Reclaims the page: flushes page content if needed, and release page buffer.
   * Page must be in state=TOMBSTONE (throws AssertionError otherwise)
   */
  private void unmapPageAndReclaimBuffer(final @NotNull PageImpl pageToReclaim) {
    if (!pageToReclaim.isPreTombstone()) {
      throw new AssertionError("Bug: page must be PRE_TOMBSTONE: " + pageToReclaim);
    }
    if (pageToReclaim.isDirty()) {
      //MAYBE RC: flush() could be off-loaded to IO thread pool, instead of slowing down housekeeper
      //         thread
      try {
        pageToReclaim.flush();
      }
      catch (IOException e) {
        throw new UncheckedIOException("Can't flush page: " + pageToReclaim, e);
      }
    }

    final ByteBuffer data = pageToReclaim.detachTombstoneBuffer();
    if (data.isDirect()) {
      DirectByteBufferAllocator.ALLOCATOR.release(data);
      totalNativeBytesCached.addAndGet(-data.capacity());
      statistics.pageReclaimedNative(data.capacity());
    }
    else {
      totalHeapBytesCached.addAndGet(-data.capacity());
      statistics.pageReclaimedHeap(data.capacity());
    }
    pageToReclaim.entomb();
  }

  @NotNull
  protected ByteBuffer allocatePageBuffer(final int bufferSize) {
    checkNotClosed();
    final boolean reclaimedSuccessfully = tryReclaimEnoughPages(bufferSize, MAX_PAGES_TO_RECLAIM_AT_ONCE);
    if (reclaimedSuccessfully) {
      final ByteBuffer buffer = DirectByteBufferAllocator.ALLOCATOR.allocate(bufferSize);
      totalNativeBytesCached.addAndGet(buffer.capacity());
      statistics.pageAllocatedDirect(bufferSize);
      return buffer;
    }
    else {
      //RC: instead of forcing existing pages to unload -- allocate heap buffers.
      //    (And after that, get them out ASAP during regular background scans?..)
      totalHeapBytesCached.addAndGet(bufferSize);
      statistics.pageAllocatedHeap(bufferSize);
      return ByteBuffer.allocate(bufferSize);
    }
  }

  /**
   * Methods tries to reclaim some pages from .pagesToProbablyReclaim queue so what cache has free
   * capacity for additional bufferSize. Method tries to reclaim no more than maxPagesToTry, and
   * return false if cache capacity is still exhausted after what, or true if cache has at least
   * bufferSize of free capacity
   */
  private boolean tryReclaimEnoughPages(final int bufferSize,
                                        final int maxPagesToTry) {
    int dirtyPagesSkipped = 0;
    for (int i = 0; i < maxPagesToTry
                    && totalNativeBytesCached.get() > cacheCapacityBytes - bufferSize; i++) {
      final PageImpl candidateToReclaim = pagesToProbablyReclaimQueue.poll();
      if (candidateToReclaim == null) {
        return false;//nothing more to reclaim
      }
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
        //    Nth dirty page with probability N/MAX_PAGES_TO_RECLAIM_AT_ONCE:
        final int rnd10 = ThreadLocalRandom.current().nextInt(maxPagesToTry);
        if (dirtyPagesSkipped <= rnd10) {
          dirtyPagesSkipped++;
          pagesToProbablyReclaimQueue.offer(candidateToReclaim);
          //MAYBE RC: also queue async flush for the page?
          continue;
        }
      }

      final boolean succeed = candidateToReclaim.tryMoveTowardsPreTombstone(/*entombYoung: */false);
      if (succeed) {
        // > 1 thread could try to reclaim the page, but we win the race:
        unmapPageAndReclaimBuffer(candidateToReclaim);
      }
    }
    return true;
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

    public void startCollectingTurn() {
      pagesForReclaimDirty = new ArrayList<>(5);
      pagesForReclaimNonDirty = new ArrayList<>(5);
    }

    public void finishCollectingTurn() {
      pagesForReclaimDirty.sort(BY_USEFULNESS);
      pagesForReclaimNonDirty.sort(BY_USEFULNESS);
    }

    public List<PageImpl> pagesForReclaimNonDirty() { return pagesForReclaimNonDirty; }

    public List<PageImpl> pagesForReclaimDirty() { return pagesForReclaimDirty; }

    public ConcurrentLinkedQueue<PageImpl> pagesForReclaimAsQueue() {
      final ConcurrentLinkedQueue<PageImpl> pagesForReclaim = new ConcurrentLinkedQueue<>();
      pagesForReclaim.addAll(pagesForReclaimNonDirty);
      pagesForReclaim.addAll(pagesForReclaimDirty);
      return pagesForReclaim;
    }

    public int totalPagesPreparedToReclaim() {
      return pagesForReclaimDirty.size() + pagesForReclaimNonDirty.size();
    }

    public void collectMoreAggressively() {
      final int currentPercentage = lowUsefulnessThresholdEstimator.percentileToEstimate();
      if (currentPercentage < maxPercentOfPagesToPrepareForReclaim) {
        lowUsefulnessThresholdEstimator.updateTargetPercentile(currentPercentage + 1);
      }
    }

    public void collectLessAggressively() {
      final int currentPercentage = lowUsefulnessThresholdEstimator.percentileToEstimate();
      if (currentPercentage > minPercentOfPagesToPrepareForReclaim) {
        lowUsefulnessThresholdEstimator.updateTargetPercentile(currentPercentage - 1);
      }
    }

    /**
     * Try to ensure cleanPagesForReclaim/totalPagesForReclaim >= fractionOfCleanPagesToAim
     * If there are fewer clean pages than requested -> try to flush some dirty pages, until
     * the fractionOfCleanPagesToAim is satisfied.
     *
     * @return how many pages were flushed as a result
     */
    public int ensureEnoughCleanPagesToReclaim(final double fractionOfCleanPagesToAim) {
      final int totalPagesToReclaim = pagesForReclaimDirty.size() + pagesForReclaimNonDirty.size();
      final int dirtyPagesTargetCount = (int)((1 - fractionOfCleanPagesToAim) * totalPagesToReclaim);
      final int pagesToFlush = pagesForReclaimDirty.size() - dirtyPagesTargetCount;
      if (pagesToFlush > 0) {
        int actuallyFlushed = 0;
        for (int i = 0; i < pagesToFlush; i++) {
          final PageImpl page = pagesForReclaimDirty.get(i);
          if (page.isTombstone()) {
            continue;
          }
          try {
            //TODO RC: flush() could be off-loaded to IO thread pool, instead of slowing down housekeeper
            //         thread
            page.flush();
            actuallyFlushed++;
          }
          catch (IOException e) {
            LOG.warn("Can't flush page " + page, e);
          }
        }
        return actuallyFlushed;
      }
      return 0;
    }

    private void checkPageGoodForReclaim(final @NotNull PageImpl page) {
      final int tokensOfUsefulness = page.tokensOfUsefulness();
      final double lowUsefulnessThreshold = lowUsefulnessThresholdEstimator.updateEstimation(tokensOfUsefulness);
      if (page.isUsable()
          && page.usageCount() == 0
          && tokensOfUsefulness <= lowUsefulnessThreshold) {
        addCandidateForReclaim(page);
      }
    }

    private void addCandidateForReclaim(final @NotNull PageImpl page) {
      if (page.isDirty()) {
        pagesForReclaimDirty.add(page);
      }
      else {
        pagesForReclaimNonDirty.add(page);
      }
    }
  }
}
