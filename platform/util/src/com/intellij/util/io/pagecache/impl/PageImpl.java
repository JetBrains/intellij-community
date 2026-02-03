// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache.impl;

import com.intellij.util.io.ByteBufferUtil;
import com.intellij.util.io.FilePageCacheLockFree;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.PagedFileStorageWithRWLockedPageContent;
import com.intellij.util.io.pagecache.Page;
import com.intellij.util.io.pagecache.PageUnsafe;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.Flushable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * Base for {@link Page} implementations -- implements fundamental parts of {@link Page} functionality,
 * and defines internal methods to be used by file page cache machinery. {@link PageImpl} is internal for
 * file page cache implementation, so even though the class itself and a lot of its methods are public,
 * they are intended to be used only by the file page cache machinery. Only {@link Page} implementation
 * methods are 'public' for use outside page cache implementation.
 * <p>
 * {@link PageImpl} consists of 3 pieces: page state (and transitions), page data (buffer + modified
 * region), and 'tokens of usefulness' (used by page cache for page eviction/reclamation). Page state
 * and tokens of usefulness are lock-free (modified with CAS), while page data buffer & modified region are
 * protected with pageLock FIXME this part is to be moved down to implementations
 * <p>
 * <b>Page state</b>: (see {@link #statePacked}) is a pair
 * <code>(NOT_READY_YET | LOADING | USABLE | ABOUT_TO_UNMAP | PRE_TOMBSTONE | TOMBSTONE)x(usageCount)</code>,
 * there usageCount is number of clients using page right now. During its lifecycle, the page goes
 * through those states in the same order:
 * FIXME RC: ABOUT_TO_UNMAP state changed so it is only with usageCount==0 -- adjust docs below accordingly.
 * <pre>
 *    NOT_READY_YET   (usageCount == 0) page has no buffer and no data
 * -> LOADING         (usageCount == 0) page buffer is allocating and content is loading from disk
 * -> USABLE          (usageCount >= 0) page is used by clients
 * -> ABOUT_TO_UNMAP  (usageCount >= 0) not accept new clients, but current clients continue to use it
 * -> PRE_TOMBSTONE   (usageCount == 0) cleanup: flush, reclaim page buffer...
 * -> TOMBSTONE       (usageCount == 0) occupies slot in PageTable, waiting until rehash cleans it
 * </pre>
 * More strictly, allowed transitions are:
 * <pre>
 * NOT_READY_YET   (usageCount: 0                                 )
 *                              ↓
 * LOADING         (usageCount: 0                                 )
 *                              ↓
 * USABLE          (usageCount: 0 <-> 1 <-> 2 <-> 3 <-> 4 <-> ... )
 *                              ↓     ↓     ↓     ↓     ↓     ↓
 * ABOUT_TO_UNMAP  (usageCount: 0 <-  1 <-  2 <-  3 <-  4 <-  ... )
 *                              ↓
 * PRE_TOMBSTONE   (usageCount: 0                                 )
 *                              ↓
 * TOMBSTONE       (usageCount: 0                                 )
 * </pre>
 * (there is also a 'short-circuit' transition NOT_READY_YET -> PRE_TOMBSTONE which is used on storage close)
 * <p>
 * Page is only visible to the 'clients' within state USABLE and ABOUT_TO_UNMAP -- other states are
 * used only internally, and a well-behaving client should not have access to the pages in such a states.
 * (Well-behaving client is the client that follows API spec, e.g. doesn't use the page after {@link #release()})
 * Because only USABLE and ABOUT_TO_UNMAP could be visible to the clients, only in those 2 states
 * .usageCount could be >0.
 * <p>
 * Transition graph is uni-directional: e.g. there is no way for page to return from ABOUT_TO_UNMAP to
 * USABLE. So pages are not re-usable: page _buffers_ could be reused, but as the page transitions to
 * ABOUT_TO_UNMAP, there is no way back -- page eventually but inevitably go towards TOMBSTONE, after
 * which it could only be thrown away to GC. This 'equifinality' is an important property of the state
 * graph: implementation relies on it in many places.
 * FIXME RC: doc needs to be adjusted: NOT_READY_YET <-> LOADING transition is actually bi-directional
 * now -- page could be returned to NOT_READY_YET state if loading failed somehow.
 * <p>
 * Transitions between states are implemented with CAS, so they could be tried concurrently, and only
 * one thread 'wins' the transition -- this is the thread that is responsible for some actions attached
 * to transition. E.g. thread that 'wins' => PRE_TOMBSTONE transition is responsible for page cleanup:
 * flush modified data if needed, and reclaim page buffer. This way only one thread is doing cleanup,
 * without using locks.
 *
 * @see FilePageCacheLockFree
 * @see PagesTable
 * @see PagedFileStorageWithRWLockedPageContent
 */
@ApiStatus.Internal
public abstract class PageImpl implements Page, Flushable, PageUnsafe {

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
   * Page is in this state while it's content is loaded from disk and other things are set up.
   * In this state the page is not usable yet (hence usageCount=0), and should not yet be visible
   * for the clients.
   * This state introduced to restrict loading to a single thread without using locking: the
   * page in this state is exclusively owned by the thread which 'wins' the CAS-transition from
   * NOT_READY_YET, and no other threads are allowed to interfere. See general description of
   * page states & transitions in class javadoc, and PRE_TOMBSTONE state description for another
   * use of the same idea.
   */
  public static final int STATE_LOADING = 1;

  /**
   * Page is allocated, loaded, and could be used. In this state, usageCount could be >=0.
   * Only transition to STATE_ABOUT_TO_UNMAP is allowed.
   */
  public static final int STATE_USABLE = 2;

  /**
   * Page is prepared to unmap and release/reclaim. Clients who already get the Page before transition
   * to that state -- could continue to use it, but new clients are not allowed to get the Page. Hence,
   * in this state .usageCount could be >0, but could not increase anymore -- only decrease.
   * Only transition to STATE_PRE_TOMBSTONE is allowed.
   */
  public static final int STATE_ABOUT_TO_UNMAP = 3;

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
  public static final int STATE_PRE_TOMBSTONE = 4;

  /**
   * Page is unmapped, its buffer is released/reclaimed -- i.e. there no page anymore, only remnant.
   * Terminal state: no transition allowed, tombstone Page occupies slot in a PageTable until it is
   * either replaced with another Page, or removed during resize/rehash.
   */
  public static final int STATE_TOMBSTONE = 5;

  private static final AtomicIntegerFieldUpdater<PageImpl> STATE_UPDATER = AtomicIntegerFieldUpdater.newUpdater(
    PageImpl.class,
    "statePacked"
  );

  private static final AtomicIntegerFieldUpdater<PageImpl> TOKENS_UPDATER = AtomicIntegerFieldUpdater.newUpdater(
    PageImpl.class,
    "tokensOfUsefulness"
  );


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
  private volatile int statePacked = packState(STATE_NOT_READY_YET, /*usageCount: */ 0);

  /**
   * During page lifecycle some additional information could be put here, such as exceptions
   * swallowed, or the thread currently operating on the page.
   */
  private volatile Object auxDebugData = null;

  /**
   * The buffer position/limit shouldn't be touched, since it is hard to make that concurrent:
   * buffer content _could_ be co-used by many parties, but limit/position could not.
   * <p>
   * Access to .data content must be guarded with .pageLock. Modification of .data ref itself
   * is lock-free, conditioned on state: .data is guaranteed to be non-null in
   * [USABLE and ABOUT_TO_UNMAP] states, and should be accessed in those states only.
   */
  //@GuardedBy("pageLock")
  protected ByteBuffer data = null;


  /**
   * Page needs to inform storage about changes in its state, and also ask storage to flush its data on disk
   * -- this handle is an interface for that.
   */
  protected final @NotNull PageToStorageHandle storageHandle;


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
                   final @NotNull PageToStorageHandle storageHandle) {
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
    this.storageHandle = storageHandle;
  }

  protected PageImpl(final int pageIndex,
                     final int pageSize,
                     final @NotNull PageToStorageHandle storageHandle) {
    this(pageIndex * (long)pageSize, pageIndex, pageSize, storageHandle);
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


  //========= Page.state management methods: ==================

  public boolean isNotReadyYet() {
    return inState(STATE_NOT_READY_YET);
  }

  public boolean isLoading() {
    return inState(STATE_LOADING);
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

  protected int state() {
    return unpackState(this.statePacked);
  }

  public int usageCount() {
    return unpackUsageCount(this.statePacked);
  }

  /**
   * [NOT_READY_YET => USABLE] page transition: tries to transition page into a LOADING state, and if
   * succeed -- load and assigns the page buffer, and set state to {@linkplain #STATE_USABLE}.
   * Method returns true if page was actually loaded -- i.e. we won CAS transition [NOT_READY_YET => LOADING],
   * and really load page content. If page is not NOT_READY_YET, or we lost the [NOT_READY_YET => LOADING]
   * transition (i.e. somebody else loads the page) -- method returns false.
   */
  public boolean tryPrepareForUse(final @NotNull PageContentLoader contentLoader) throws IOException {
    final int packedState = this.statePacked;
    final int state = unpackState(packedState);
    if (state == STATE_NOT_READY_YET) {
      final int usageCount = unpackUsageCount(packedState);
      if (usageCount != 0) {
        throw new AssertionError("Bug: usageCount(=" + usageCount + ") must be 0 in NOT_READ_YET: " + this);
      }

      final int newPackedState = packState(STATE_LOADING, 0);

      if (STATE_UPDATER.compareAndSet(this, packedState, newPackedState)) {
        this.auxDebugData = Thread.currentThread();
        try {
          this.data = contentLoader.loadPageContent(this);
          //Current thread exclusively owns the page in LOADING state, so no need for CAS here:
          this.statePacked = packState(STATE_USABLE, 0);
          return true;
        }
        catch (Throwable e) {
          //MAYBE RC: it is better to immediately transition page to TOMBSTONE? This simplifies logic:
          //          no 'backward' transitions, page either loaded, or failed and entombed, and it is
          //          up PagedStorage implementation to decide do another attempt to load page or not.
          //          Downside is that now (* -> TOMBSTONE) transition is confined to housekeeper thread,
          //          and that confinement become broken -- needs to investigate consequences.

          //return the page to NOT_READY_YET state, so it could be either re-tried to load, or entombed
          this.statePacked = packState(STATE_NOT_READY_YET, 0);
          //MAYBE RC: log the exception?
          this.auxDebugData = e;
          throw e;
        }
      }
    }
    return false;
  }

  /**
   * Tries to acquire the page for use -- i.e. check page state=USABLE and increment .usageCount.
   * Page should not be accessed externally without this method called prior to what.
   * {@link #release()}/{@link #close()} must be called after this method to release the page.
   * <p>
   * Method returns false if the page is NOT_READY_YET | LOADING -- because in this case page
   * likely becomes USABLE after a while, and it is worth to try acquiring it again later.
   * <p>
   * Contrary to that, if the page state is [ABOUT_TO_RECLAIM | TOMBSTONE] -- method throws
   * IOException, because in those cases repeating is useless: the page will never become
   * USABLE again.
   *
   * @param acquirer who acquires the page.
   *                 Argument is not utilized now, reserved for debug leaking pages purposes.
   * @return false if page can't be acquired because it is not USABLE yet (so it is worth to
   * try acquiring again), true if page is acquired for use
   * @throws IOException if page state is [ABOUT_TO_RECLAIM | TOMBSTONE], so it is not worth
   *                     to try acquiring again
   */
  public boolean tryAcquireForUse(@SuppressWarnings("unused") final Object acquirer) throws IOException {
    while (true) {//CAS loop:
      final int packedState = statePacked;
      final int state = unpackState(packedState);
      final int usageCount = unpackUsageCount(packedState);
      if (state < STATE_USABLE) {
        return false;
      }
      else if (state > STATE_ABOUT_TO_UNMAP) {
        throw new IOException("Page.state[=" + state + "] != USABLE");
      }
      else if (state == STATE_ABOUT_TO_UNMAP) {
        throw new IOException("Page.state[=" + state + "] != USABLE");
      }
      //else -> try to acquire:

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
  public boolean tryAcquire(final Object acquirer) {
    try {
      if (tryAcquireForUse(acquirer)) {
        return true;
      }//else: not ready yet
    }
    catch (IOException ignored) {
      //already on reclamation path
    }

    return false;
  }

  @Override
  public void release() {
    while (true) {
      final int packedState = statePacked;
      final int state = unpackState(packedState);
      final int usageCount = unpackUsageCount(packedState);
      if (state != STATE_USABLE && state != STATE_ABOUT_TO_UNMAP) {
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
            final int newPackedState = packState(STATE_PRE_TOMBSTONE, 0);
            if (STATE_UPDATER.compareAndSet(this, packedState, newPackedState)) {
              return true;
            }
          }
          return false;
        }
        case STATE_LOADING: {
          //page in this state is exclusively owned by the thread won [NOT_READY_YET=>LOADING] transition
          return false;
        }
        case STATE_USABLE: {
          if (usageCount > 0) {
            return false;
          }
          final int newPackedState = packState(STATE_ABOUT_TO_UNMAP, 0);
          if (STATE_UPDATER.compareAndSet(this, packedState, newPackedState)) {
            continue;
          }
          return false;
        }
        case STATE_ABOUT_TO_UNMAP: {
          if (usageCount > 0) {
            //return false;
            throw new AssertionError("Page[ABOUT_TO_UNMAP].usageCount=" + usageCount + " -- must be 0. " + this);
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
      throw new AssertionError("Bug: page.state(=" + state + ") be PRE_TOMBSTONE. " + this);
    }

    final int newPackedState = packState(STATE_TOMBSTONE, 0);
    if (!STATE_UPDATER.compareAndSet(this, packedState, newPackedState)) {
      throw new AssertionError("Bug: somebody interferes with PRE_TOMBSTONE->TOMBSTONE transition. " + this);
    }
  }

  /**
   * Detaches .data buffer from the page: returns .data and assign .data=null.
   * Page must be [PRE_TOMBSTONE] to call this method, hence page is exclusively owned by a
   * thread promoting it to PRE_TOMBSTONE, hence no synchronization required.
   * Method can be invoked only once -- subsequent invocations will throw an exception.
   */
  public ByteBuffer detachTombstoneBuffer() {
    if (!isPreTombstone()) {
      throw new AssertionError("Bug: only PRE_TOMBSTONES could detach buffer. " + this);
    }
    final ByteBuffer buffer = this.data;
    if (buffer == null) {
      throw new AssertionError("Bug: buffer already detached, .data is null. " + this);
    }
    this.data = null;
    return buffer;
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

  public abstract boolean isDirty();

  @Override
  public abstract void flush() throws IOException;

  /**
   * Method tries flushing page content on disk, but only if there is a way to do so without waiting for
   * some writes to finish -- i.e. page is not write-locked in any way. Otherwise method just returns false.
   */
  public abstract boolean tryFlush() throws IOException;

  @Override
  public void close() {
    release();
  }

  /* ========= page utility accounting: ============================================================= */

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

  /* =============== content access methods: ======================================================== */

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
  @Override
  @ApiStatus.Obsolete
  public ByteBuffer duplicate() {
    checkPageIsValidForAccess();
    return data.duplicate().order(data.order());
  }

  /**
   * Checks page in a state there it could be read/write, throws {@link IllegalStateException}
   * otherwise
   *
   * @throws IllegalStateException if page is not in a state there it could be accessed (read/write)
   */
  protected void checkPageIsValidForAccess() {
    if (!isUsable() && !isAboutToUnmap()) {
      throw new IllegalStateException("Page state must be in { USABLE | ABOUT_TO_UNMAP } for accessing, but: " + this);
    }
    if (usageCount() == 0) {
      throw new IllegalStateException("Page must be acquired for use (i.e. usageCount>0) before accessing, but: " + this);
    }
  }

  @Override
  public String toString() {
    final int packedState = statePacked;
    return "Page[#" + pageIndex + ", size: " + pageSize + "b, offsetInFile: " + offsetInFile + "b]" +
           "{" +
           "state: " + unpackState(packedState) + ", " +
           "inUse: " + unpackUsageCount(packedState) +
           "}" + ((auxDebugData != null) ? ", aux: " + auxDebugData : "");
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
