// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache.impl;

import com.intellij.openapi.util.ThrowableNotNullFunction;
import com.intellij.util.io.ByteBufferUtil;
import com.intellij.util.io.FilePageCacheLockFree;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.pagecache.Page;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * {@link Page} implementation. PageImpl is internal for file page cache implementation, so even
 * though the class itself and a lot of its methods are public, they are intended to be used only
 * by the file page cache machinery. Only {@link Page} implementation methods are 'public' for
 * use outside page cache implementation.
 * <p>
 * Page contains 3 pieces of information: page state, page data (buffer + modified region), and
 * 'tokens of usefulness' (used by page cache for page eviction/reclamation). Page state and tokens
 * of usefulness are lock-free (modified with CAS), while page data buffer & modified region are
 * protected with pageLock.
 * <p>
 * <b>Page state</b>: (see {@link #statePacked}) is a pair
 * <code>(NOT_READY_YET | USABLE | ABOUT_TO_UNMAP | PRE_TOMBSTONE | TOMBSTONE)x(usageCount)</code>,
 * there usageCount is number of clients using page right now. During its lifecycle, the page goes
 * through those states in the same order:
 * <pre>
 *    NOT_READY_YET   (usageCount == 0) page buffer allocation & data loading
 * -> USABLE          (usageCount >= 0) page is used by clients
 * -> ABOUT_TO_UNMAP  (usageCount >= 0) not accept new clients, but current clients continue to use it
 * -> PRE_TOMBSTONE   (usageCount == 0) cleanup: flush, reclaim page buffer...
 * -> TOMBSTONE       (usageCount == 0) occupies slot in PageTable, waiting until rehash cleans it
 * </pre>
 * More strictly, allowed transitions are:
 * <pre>
 * NOT_READY_YET   (usageCount: 0                                 )
 *                              ↓
 * USABLE          (usageCount: 0 <-> 1 <-> 2 <-> 3 <-> 4 <-> ... )
 *                              ↓     ↓     ↓     ↓     ↓     ↓
 * ABOUT_TO_UNMAP  (usageCount: 0 <-  1 <-  2 <-  3 <-  4 <-  ... )
 *                              ↓
 * PRE_TOMBSTONE   (usageCount: 0                                 )
 *                              ↓
 * TOMBSTONE       (usageCount: 0                                 )
 * </pre>
 * (there is also a 'shortcut' transition NOT_READY_YET -> PRE_TOMBSTONE which is used on storage close)
 * <p>
 * Page is only visible to the 'clients' within state USABLE and ABOUT_TO_UNMAP -- other states are
 * used only internally. Because only USABLE and ABOUT_TO_UNMAP could be visible to the clients, only
 * in those 2 states .usageCount could be >0.
 * <p>
 * Transition graph is uni-directional: e.g. there is no way for page to return from ABOUT_TO_UNMAP to
 * USABLE. So pages are not re-usable: page _buffers_ could be reused, but as the page transitions to
 * ABOUT_TO_UNMAP, there is no way back -- page will inevitably go towards TOMBSTONE, after which it
 * could be only thrown away to GC. This 'equifinality' is an important property of the state graph,
 * implementation relies on it in many places.
 * <p>
 * Transitions between states are implemented with CAS, so they could be tried concurrently, and only
 * one thread 'wins' the transition -- this is the thread that is responsible for some actions attached
 * to transition. E.g. thread that 'wins' => PRE_TOMBSTONE transition is responsible for page cleanup:
 * flush modified data if needed, and reclaim page buffer. This way only one thread is doing cleanup,
 * without using locks.
 *
 * @see FilePageCacheLockFree
 * @see PagesTable
 * @see com.intellij.util.io.PagedFileStorageLockFree
 */
@ApiStatus.Internal
public class PageImpl implements Page {

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
   * Only transition to STATE_PRE_TOMBSTONE is allowed.
   */
  public static final int STATE_ABOUT_TO_UNMAP = 2;

  /**
   * Intermediate state between (ABOUT_TO_UNMAP, usedCount=0) and TOMBSTONE. Only transition to
   * STATE_TOMBSTONE is allowed if !isDirty().
   * <p>
   * State is a bit superficial, it is introduced to remove contention on cleanup procedures:
   * thread that transitions page from (ABOUT_TO_UNMAP, usedCount=0) to PRE_TOMBSTONE is the
   * thread responsible for final cleanup: flush the page if it is dirty, and reclaim the page
   * buffer.
   * <p>
   * The key point here is: cleanup procedures are thread-safe, but not concurrent: e.g. .flush()
   * is blocking. If we allow initiate cleanup straight from the state (ABOUT_TO_UNMAP,usedCount=0),
   * then it is quite possible >1 threads try that, and all but one be blocked on a lock inside
   * .flush() -- which is useless.
   * <p>
   * Instead, all such threads contend on transition (ABOUT_TO_UNMAP,usedCount=0) -> PRE_TOMBSTONE
   * which is non-blocking, CAS-based. One thread wins that CAS, and follows along with the
   * cleanups without any interference, while other threads are free to do anything else.
   */
  public static final int STATE_PRE_TOMBSTONE = 3;

  /**
   * Page is unmapped, its buffer is released/reclaimed -- i.e. there no page anymore, only remnant.
   * Terminal state: no transition allowed, tombstone Page occupies slot in a PageTable until it is
   * either replaced with another Page, or removed during resize/rehash.
   */
  public static final int STATE_TOMBSTONE = 4;

  private static final AtomicIntegerFieldUpdater<PageImpl> STATE_UPDATER = AtomicIntegerFieldUpdater.newUpdater(
    PageImpl.class,
    "statePacked"
  );

  private static final AtomicIntegerFieldUpdater<PageImpl> TOKENS_UPDATER = AtomicIntegerFieldUpdater.newUpdater(
    PageImpl.class,
    "tokensOfUsefulness"
  );


  private static final long EMPTY_MODIFIED_REGION = 0;

  /** Mask to extract 'usageCount' from {@link #statePacked} */
  private static final int USAGE_COUNT_MASK = 0x00FF_FFFF;

  private final int pageSize;

  /** 0-based page index == offsetInFile/pageSize */
  private final int pageIndex;

  /** Offset of the page first byte in the source file */
  private final transient long offsetInFile;

  /**
   * Page state, as (state, usageCount), packed into a single int for atomic update. Highest 1 byte
   * is state{STATE_NOT_READY_YET | STATE_USABLE | STATE_OUT}, lowest 3 bytes ({@link #USAGE_COUNT_MASK})
   * are usageCount -- number of clients currently using this page ({@link #tryAcquireForUse(Object)}/{@link #release()})
   */
  private volatile int statePacked = 0;

  /** Mostly guards access to .data buffer */
  private final transient ReentrantReadWriteLock pageLock = new ReentrantReadWriteLock();

  /**
   * The buffer position/limit shouldn't be touched, since it is hard to make that concurrent:
   * buffer content _could_ be co-used by many parties, but limit/position could not.
   * <p>
   * Access to .data content must be guarded with .pageLock. Modification of .data ref itself
   * is lock-free, conditioned on state: .data is guaranteed to be non-null in
   * [USABLE and ABOUT_TO_UNMAP] states, and should be accessed in those states only.
   */
  //@GuardedBy("pageLock")
  private ByteBuffer data = null;

  /**
   * Modified region [minOffsetModified, maxOffsetModified) of {@linkplain #data}, packed into a
   * single long for lock-free reads.
   * <p>
   * {@code modifiedRegionPacked = (maxOffsetModified<<32) | minOffsetModified}
   * <p>
   * maxOffsetModified is basically ~ buffer.position(), i.e. first byte to store,
   * maxOffsetModifiedExclusive is ~ buffer.limit() -- i.e. the _next_ byte after
   * the last byte to store.
   * <p>
   * Modified under 'this' intrinsic lock, same as guard the .flush() method
   */
  private volatile long modifiedRegionPacked = EMPTY_MODIFIED_REGION;

  private final @NotNull PageToStorageHandle pageToStorageHandle;


  /** for reclamation management */
  private volatile int tokensOfUsefulness = FilePageCacheLockFree.TOKENS_INITIALLY;
  /**
   * This is a copy of tokensOfUsefulness, but modified only by the housekeeper thread. This way
   * housekeeper thread may use the field as thread-local field, without accounting for concurrent
   * modifications. On the contrary, tokensOfUsefulness could be modified anytime, even in the middle
   * of some action -- which is sometimes unwelcome. E.g. one couldn't use tokensOfUsefulness for
   * sorting: most sorting algos require a stable comparator, but due to concurrent modifications
   * comparison by tokensOfUsefulness violates this requirement.
   */
  private int tokensOfUsefulnessLocal = 0;

  private PageImpl(final long offsetInFile,
                   final int pageIndex,
                   final int pageSize,
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
    this.pageToStorageHandle = pageToStorageHandle;
  }

  public PageImpl(final int pageIndex,
                  final int pageSize,
                  final @NotNull PageToStorageHandle pageToStorageHandle) {
    this(pageIndex * (long)pageSize, pageIndex, pageSize, pageToStorageHandle);
  }

  public static PageImpl notReady(final int index,
                                  final int pageSize,
                                  final @NotNull PageToStorageHandle pageToStorageHandle) {
    final PageImpl page = new PageImpl(index, pageSize, pageToStorageHandle);
    page.statePacked = packState(STATE_NOT_READY_YET, 0);
    return page;
  }

  @Override
  public int pageSize() {
    return pageSize;
  }

  @Override
  public int pageIndex() { return pageIndex; }

  @Override
  public long offsetInFile() {
    return offsetInFile;
  }

  @Override
  public long lastOffsetInFile() {
    return (pageIndex + 1L) * pageSize - 1;
  }


  //===== locking methods: ====================================

  @Override
  public void lockPageForWrite() {
    pageLock.writeLock().lock();
  }

  @Override
  public void unlockPageForWrite() {
    pageLock.writeLock().unlock();
  }

  @Override
  public void lockPageForRead() {
    pageLock.readLock().lock();
  }

  @Override
  public void unlockPageForRead() {
    pageLock.readLock().unlock();
  }

  public ReentrantReadWriteLock pageLock() {
    return pageLock;
  }

  //========= Page.state management methods: ==================

  public boolean isNotReadyYet() {
    return inState(STATE_NOT_READY_YET);
  }

  @Override
  public boolean isUsable() {
    return inState(STATE_USABLE);
  }

  public boolean isAboutToUnmap() {
    return inState(STATE_ABOUT_TO_UNMAP);
  }

  public boolean isPreTombstone() {
    return inState(STATE_PRE_TOMBSTONE);
  }

  public boolean isTombstone() {
    return inState(STATE_TOMBSTONE);
  }

  public boolean inState(final int expectedState) {
    return unpackState(statePacked) == expectedState;
  }

  public int usageCount() {
    return unpackUsageCount(this.statePacked);
  }

  /**
   * [NOT_READY_YET => USABLE] page transition: sets buffer with data, and set state to
   * {@linkplain #STATE_USABLE}. Page must be in {@linkplain #STATE_NOT_READY_YET} before
   * call to this method, otherwise {@linkplain AssertionError} will be thrown
   */
  public void prepareForUse(final @NotNull PageContentLoader contentLoader) throws IOException {
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

  /**
   * Tries to acquire the page for use -- i.e. check page state=USABLE and increment .usageCount.
   * Page should not be accessed externally without this method called prior to what.
   * {@link #release()}/{@link #close()} must be called after this method to release the page.
   * <p>
   * Method returns false if page is NOT_READY_YET -- because in this case page likely becomes
   * USABLE after a while, and it is worth to try acquiring it again later.
   * <p>
   * Contrary to that, if page state is [ABOUT_TO_RECLAIM | TOMBSTONE] -- method throws IOException,
   * because in those cases repeating is useless, page will never become USABLE.
   *
   * @param acquirer who acquires the page.
   *                 Argument is not utilized now, reserved for debug leaking pages purposes.
   * @return false if page can't be acquired because it is NOT_READY_YET, true if page is acquired
   * for use
   * @throws IOException if page state is [ABOUT_TO_RECLAIM | TOMBSTONE]
   */
  public boolean tryAcquireForUse(final Object acquirer) throws IOException {
    while (true) {//CAS loop:
      final int packedState = statePacked;
      final int state = unpackState(packedState);
      final int usageCount = unpackUsageCount(packedState);
      if (state == STATE_NOT_READY_YET) {
        return false;
      }
      else if (state != STATE_USABLE) {
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

  @Override
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
        addTokensOfUsefulness(FilePageCacheLockFree.TOKENS_PER_USE * usageCount);
        break;
      }
    }
  }

  /**
   * Moves page along the state transition graph towards PRE_TOMBSTONE state as much as it is possible.
   *
   * @return true if PRE_TOMBSTONE state is reached during the call -- i.e. there was at least one
   * transition, and the final state is PRE_TOMBSTONE. false if something prevents transition to
   * that state, or page is already in that state.
   */
  public boolean tryMoveTowardsPreTombstone(final boolean entombYoung) {
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
            final int newPackedState = packState(STATE_PRE_TOMBSTONE, 0);
            if (STATE_UPDATER.compareAndSet(this, packedState, newPackedState)) {
              return true;
            }
          }
          return false;
        }
        case STATE_USABLE: {
          final int newPackedState = packState(STATE_ABOUT_TO_UNMAP, usageCount);
          if (STATE_UPDATER.compareAndSet(this, packedState, newPackedState)) {
            continue;
          }
        }
        case STATE_ABOUT_TO_UNMAP: {
          if (usageCount > 0) {
            return false;
          }
          final int newPackedState = packState(STATE_PRE_TOMBSTONE, 0);
          if (STATE_UPDATER.compareAndSet(this, packedState, newPackedState)) {
            return true;
          }
        }
        case STATE_PRE_TOMBSTONE: {
          return false;
        }
        case STATE_TOMBSTONE: {
          return false;
        }
        default: {
          throw new AssertionError("Code bug: unknown state " + state + ": " + this);
        }
      }
    }
  }

  /**
   * [PRE_TOMBSTONE => TOMBSTONE] page transition: terminal transition. Page must be !dirty.
   * The only thread allowed to call this method is the thread wins the transition => PRE_TOMBSTONE
   * -- i.e. there should be no competition/concurrency on this method call.
   */
  public void entomb() {
    if (isDirty()) {
      throw new AssertionError("Bug: page must be !dirty to be TOMBSTONE-ed, but: " + this);
    }
    //Really, on PRE_TOMBSTONE it should be no competition -> only one thread is responsible
    //  for the page transition below PRE_TOMBSTONE. But be safe, and do the CAS anyway:
    final int packedState = statePacked;
    final int state = unpackState(packedState);
    final int usageCount = unpackUsageCount(packedState);
    if (usageCount > 0) {
      throw new AssertionError("Bug: page.usageCount(=" + usageCount + ") must be 0. page: " + this);
    }
    if (state != STATE_PRE_TOMBSTONE) {
      throw new AssertionError("Bug: page.state be PRE_TOMBSTONE, but " + state + ", page: " + this);
    }

    final int newPackedState = packState(STATE_TOMBSTONE, 0);
    if (!STATE_UPDATER.compareAndSet(this, packedState, newPackedState)) {
      throw new AssertionError("Bug: somebody interferes with PRE_TOMBSTONE->TOMBSTONE transition. " + this);
    }
  }

  /**
   * Detaches .data buffer from the page: returns .data and assign .data=null
   * Page must be [TOMBSTONE] to call this method. Method can be invoked only once -- subsequent
   * invocations will throw an exception.
   */
  public ByteBuffer detachTombstoneBuffer() {
    if (!isPreTombstone()) {
      throw new AssertionError("Bug: only PRE_TOMBSTONES could detach buffer");
    }
    //Lock to avoid races with .prepareForUse() & .flush()
    pageLock.writeLock().lock();
    try {
      final ByteBuffer buffer = this.data;
      if (buffer == null) {
        throw new AssertionError("Bug: buffer already detached, .data is null " + this);
      }
      this.data = null;
      return buffer;
    }
    finally {
      pageLock.writeLock().unlock();
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

  public int addTokensOfUsefulness(final int tokensToAdd) {
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

  public int decayTokensOfUsefulness(final int numerator,
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

  public int tokensOfUsefulness() {
    return tokensOfUsefulness;
  }

  public int localTokensOfUsefulness() {
    return tokensOfUsefulnessLocal;
  }

  public void updateLocalTokensOfUsefulness(final int tokensOfUsefulness) {
    tokensOfUsefulnessLocal = tokensOfUsefulness;
  }

  // ================ dirty region/flush control: =================================

  @Override
  public boolean isDirty() {
    return modifiedRegionPacked != EMPTY_MODIFIED_REGION;
  }

  @Override
  public void flush() throws IOException {
    //RC: flush is logically a read-op, hence only readLock is needed. But we also need to update
    //    a modified region, which is a write-op.
    //    There are few ways to deal with dirty region updates: e.g. update modifiedRegion with CAS.
    //    Together (readLock + CAS update) implicitly avoids concurrent flushes: by holding readLock
    //    we already ensure nobody _modifies_ page concurrently with us. The only possible contender
    //    is another .flush() call -- and by CAS-ing modifiedRegion to empty _before_ actual
    //    .flushBytes() we ensure such competing .flush() will short-circuit. This is the 'most
    //    concurrent' approach, but it has issues with consistency: namely, storage is notified
    //    about dirty page 'flushed' only after actual CAS updates modifiedRegion to empty. This
    //    means what other thread could see page.isDirty()=false, but at the same time
    //    storage.isDirty() = true, since page flush is not yet propagated to storage. Even worse:
    //    another thread could call page.flush() and still see storage.isDirty()=true -- because
    //    .flush() is short-circuit on early .isDirty() check.
    //
    //    This is a trade-off: 'more concurrent' data structures have 'less consistent' state --
    //    basically, state becomes more distributed. But such a tradeoff seems unjustified here:
    //    really, we don't need concurrent flushes, while 'eventually consistent' .isDirty() could
    //    create a lot of confusion for storage users. Hence, I've chosen another approach: use
    //    additional lock inside .flush() to completely avoid concurrent flushes. This lock allows
    //    to reset dirty region _after_ actual .flushBytes().
    //
    //    This still creates an 'inconsistency' though: another thread could see storage.isDirty()
    //    = false, while some page.isDirty()=true -- but I tend to think there are fewer possibilities
    //    for confusion here. Let's see how it goes.

    flushWithAdditionalLock();
  }

  private void flushWithAdditionalLock() throws IOException {
    if (isDirty()) {
      //RC: flush is logically a read-op, hence only readLock is needed. But we also need to update
      //    a modified region, which is write-op. By holding readLock we already ensure nobody
      //    _modifies_ page concurrently with us. The only possible contender is another .flush()
      //    call -- hence we use 'this' lock to avoid concurrent flushes.
      lockPageForRead();
      try {
        //'this' lock is mostly uncontended: other than here, it is guarded .regionModified() --
        // which is invoked only under pageLock.writeLock, so 'this' never contended there.
        // Here it _could_ be contended, but only against concurrent .flush() invocations,
        // which are possible, but rare. This is why I use lock instead of update modifiedRegion
        // with CAS: uncontended lock is cheap and simpler to use, and I need some lock to prevent
        // concurrent .flush() anyway.
        synchronized (this) {
          final long modifiedRegion = modifiedRegionPacked;
          if (modifiedRegion == EMPTY_MODIFIED_REGION) {
            //competing flush: somebody else already did the job
            return;
          }
          final int minOffsetModified = unpackMinOffsetModified(modifiedRegion);
          final int maxOffsetModifiedExclusive = unpackMaxOffsetModifiedExclusive(modifiedRegion);

          //we won the CAS competition: execute the flush
          final ByteBuffer sliceToSave = data.duplicate()
            .order(data.order());
          sliceToSave.position(minOffsetModified)
            .limit(maxOffsetModifiedExclusive);

          pageToStorageHandle.flushBytes(sliceToSave, offsetInFile + minOffsetModified);
          pageToStorageHandle.pageBecomeClean();
          modifiedRegionPacked = EMPTY_MODIFIED_REGION;
        }
      }
      finally {
        unlockPageForRead();
      }
    }
  }

  //@GuardedBy(pageLock.writeLock)
  @Override
  public void regionModified(final int startOffsetModified,
                             final int length) {
    assert pageLock.writeLock().isHeldByCurrentThread() : "writeLock must be held while calling this method";

    final long modifiedRegionNew;
    final long modifiedRegionOld;
    synchronized (this) {
      modifiedRegionOld = modifiedRegionPacked;
      final int minOffsetModifiedOld = unpackMinOffsetModified(modifiedRegionOld);
      final int maxOffsetModifiedOld = unpackMaxOffsetModifiedExclusive(modifiedRegionOld);

      final int minOffsetModifiedNew = Math.min(minOffsetModifiedOld, startOffsetModified);
      final int endOffset = startOffsetModified + length;
      final int maxOffsetModifiedNew = Math.max(maxOffsetModifiedOld, endOffset);

      if (minOffsetModifiedOld == minOffsetModifiedNew
          && maxOffsetModifiedOld == maxOffsetModifiedNew) {
        return;
      }


      modifiedRegionNew = ((long)minOffsetModifiedNew)
                          | (((long)maxOffsetModifiedNew) << Integer.SIZE);
      this.modifiedRegionPacked = modifiedRegionNew;
    }

    pageToStorageHandle.modifiedRegionUpdated(
      offsetInFile() + startOffsetModified,
      length
    );
    if (modifiedRegionOld == 0 && modifiedRegionNew != 0) {
      pageToStorageHandle.pageBecomeDirty();
    }
  }

  private static int unpackMinOffsetModified(final long modifiedRegionPacked) {
    return (int)modifiedRegionPacked;
  }

  private static int unpackMaxOffsetModifiedExclusive(final long modifiedRegionPacked) {
    return (int)(modifiedRegionPacked >> Integer.SIZE);
  }

  /* =============== content access methods: ======================================================== */

  //RC: I'd expect generic read/write methods to be the main API for accessing page, because
  //    they are range-lock-friendly -- if we needed to move to range locks instead of current
  //    per-page locks, read/write methods are a natural fit.

  @Override
  public <OUT, E extends Exception> OUT read(final int startOffsetOnPage,
                                             final int length,
                                             final ThrowableNotNullFunction<ByteBuffer, OUT, E> reader) throws E {
    pageLock.readLock().lock();
    try {
      checkPageIsValidForAccess();

      //RC: need to upgrade language level to 13 to use .slice(index, length)
      final ByteBuffer slice = data.duplicate()
        .order(data.order())
        .asReadOnlyBuffer();
      slice.position(startOffsetOnPage)
        .limit(startOffsetOnPage + length);
      return reader.fun(slice);
    }
    finally {
      pageLock.readLock().unlock();
    }
  }

  @Override
  public <OUT, E extends Exception> OUT write(final int startOffsetOnPage,
                                              final int length,
                                              final ThrowableNotNullFunction<ByteBuffer, OUT, E> writer) throws E {
    pageLock.writeLock().lock();
    try {
      checkPageIsValidForAccess();

      //RC: need to upgrade language level to 13 to use .slice(index, length)
      final ByteBuffer slice = data.duplicate()
        .order(data.order());
      slice.position(startOffsetOnPage)
        .limit(startOffsetOnPage + length);
      try {
        return writer.fun(slice);
      }
      finally {
        regionModified(startOffsetOnPage, length);
        checkPageIsValidForAccess();
      }
    }
    finally {
      pageLock.writeLock().unlock();
    }
  }

  @Override
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

  @Override
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

  @Override
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

  @Override
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


  @Override
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

  @Override
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

  @Override
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

  @Override
  public void putFromBuffer(final ByteBuffer data,
                            final int offsetInPage) {
    lockPageForWrite();
    try {
      checkPageIsValidForAccess();

      //RC: since java16 may use this.data.put(offsetInPage, data, 0, data.remaining());
      final int length = data.remaining();
      final ByteBuffer slice = this.data.duplicate()
        .order(data.order());

      slice.position(offsetInPage);
      slice.put(data);

      regionModified(offsetInPage, length);
    }
    finally {
      unlockPageForWrite();
    }
  }

  @Override
  public void putFromArray(final byte[] source,
                           final int offsetInArray,
                           final int offsetInPage,
                           final int length) {
    lockPageForWrite();
    try {
      checkPageIsValidForAccess();

      final ByteBuffer buf = data.duplicate()
        .order(data.order());
      buf.position(offsetInPage);
      buf.put(source, offsetInArray, length);
      regionModified(offsetInPage, length);
    }
    finally {
      unlockPageForWrite();
    }
  }

  //TODO RC: probably, it is better to return .duplicate()-ed buffer -- to avoid at least some
  //         errors causes. The performance cost should be negligible, and outweighed by the
  //         reduced chances of errors with buffer position/limit cursors manipulation
  @Override
  public ByteBuffer rawPageBuffer() {
    checkPageIsValidForAccess();
    return pageBufferUnchecked();
  }

  //BEWARE: access without any locks and state checking!
  public ByteBuffer pageBufferUnchecked() {
    return data;
  }

  //TODO RC: IntToIntBTree uses direct access to page buffer to copy range of bytes during node
  //         resize -- on split, or regular insert. Better to have dedicated Page.copyRangeTo(Page) method
  //         for that, since now one need to acquire page locks, and they must be acquired in
  //         stable order to avoid deadlocks
  @Deprecated
  protected ByteBuffer duplicate() {
    checkPageIsValidForAccess();
    return data.duplicate().order(data.order());
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
    final long modifiedRegion = modifiedRegionPacked;
    final int packedState = statePacked;
    return "Page[#" + pageIndex + ", size: " + pageSize + "b, offsetInFile: " + offsetInFile + "b]" +
           "{" +
           "state: " + unpackState(packedState) + ", " +
           "inUse: " + unpackUsageCount(packedState) +
           "}, dirtyRegion: " +
           "[" + unpackMinOffsetModified(modifiedRegion) + ".." + unpackMaxOffsetModifiedExclusive(modifiedRegion) + ") ";
  }

  @Override
  public String formatData() {
    final ByteBuffer _data = data;
    if (_data != null) {
      return IOUtil.toHexString(_data);
    }
    else {
      return "null";
    }
  }
}
