// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache.impl;

import com.intellij.openapi.util.ThrowableNotNullFunction;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Guards access to page buffer with {@link java.util.concurrent.locks.ReadWriteLock}
 */
public final class RWLockProtectedPageImpl extends PageImpl {

  private static final long EMPTY_MODIFIED_REGION = 0;

  /** Guards access to .data buffer */
  private final transient ReentrantReadWriteLock contentProtectingLock;

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


  public RWLockProtectedPageImpl(final int pageIndex,
                                 final int pageSize,
                                 final @NotNull PageToStorageHandle pageToStorageHandle,
                                 final ReentrantReadWriteLock lock) {
    super(pageIndex, pageSize, pageToStorageHandle);
    this.contentProtectingLock = lock;
  }

  public static RWLockProtectedPageImpl createBlankWithOwnLock(final int index,
                                                               final int pageSize,
                                                               final @NotNull PageToStorageHandle pageToStorageHandle) {
    final RWLockProtectedPageImpl page = new RWLockProtectedPageImpl(
      index,
      pageSize,
      pageToStorageHandle,
      new ReentrantReadWriteLock()
    );
    if (!page.isNotReadyYet()) {
      throw new AssertionError("Bug: page just created must be NOT_READY_YET, but " + page);
    }
    return page;
  }

  public static RWLockProtectedPageImpl createBlankWithExplicitLock(final int index,
                                                                    final int pageSize,
                                                                    final @NotNull PageToStorageHandle pageToStorageHandle,
                                                                    final ReentrantReadWriteLock contentProtectingLock) {
    final RWLockProtectedPageImpl page = new RWLockProtectedPageImpl(
      index,
      pageSize,
      pageToStorageHandle,
      contentProtectingLock
    );
    if (!page.isNotReadyYet()) {
      throw new AssertionError("Bug: page just created must be NOT_READY_YET, but " + page);
    }
    return page;
  }

  //===== locking methods: ====================================

  @Override
  public void lockPageForWrite() {
    contentProtectingLock.writeLock().lock();
  }

  @Override
  public void unlockPageForWrite() {
    contentProtectingLock.writeLock().unlock();
  }

  @Override
  public void lockPageForRead() {
    contentProtectingLock.readLock().lock();
  }

  @Override
  public void unlockPageForRead() {
    contentProtectingLock.readLock().unlock();
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
    //    .flush() short-circuits on early .isDirty() check.
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
    flushWithReadLock();
  }

  @Override
  public boolean tryFlush() throws IOException {
    if (!isDirty()) {
      return true;
    }

    final ReentrantReadWriteLock.ReadLock readLock = contentProtectingLock.readLock();
    if (!readLock.tryLock()) {
      return false;
    }
    try {
      flushImpl();
      return true;
    }
    finally {
      readLock.unlock();
    }
  }

  private void flushWithReadLock() throws IOException {
    if (!isDirty()) {
      return;
    }

    //We need to ensure nobody _modifies_ page content while we're flushing it. The most natural way to
    // do it is by acquiring readLock -- but we could avoid lock acquisition if page state > ABOUT_TO_UNMAP,
    // because no one but housekeeper thread could legally use (and even see) the page in those states.

    if (state() > PageImpl.STATE_ABOUT_TO_UNMAP) {
      //This branch is not merely an optimization, but deadlock/livelock-prevention: page lock is not
      // exclusively owned by page, it could be a _shared_ lock, used by many pages -- hence, it is
      // possible for this lock to be write-locked even if the page is in > ABOUT_TO_UNMAP state. That
      // would be impossible for lock exclusively owned by the page: if the page is not available for
      // anyone outside FPCache, then no one outside FPC could acquire the page lock either -- but if
      // page lock is shared, then it becomes possible.
      // Hence, consider the scenario; there page_1 and page_2 shares the same lock:
      // - Thread 1: writeLock page_1, tries to get another page_2 from the storage. page_2 happens to be
      //             in the PRE_TOMBSTONE state, and must be fully released _before_ it could be re-allocated
      //             and returned.
      // - FPC housekeeper: tries to release page_2, finds out it is dirty, tries to flush it, get locked
      //             on the (shared) pageLock (which is held by Thread 1)
      // This branch prevents the deadlock in such a cases by bypassing page lock entirely

      flushImpl();
    }
    else {
      lockPageForRead();
      try {
        flushImpl();
      }
      finally {
        unlockPageForRead();
      }
    }
  }

  private void flushImpl() throws IOException {
    //flush is logically a read-op, hence only readLock is needed. But we also need to update
    // a modified region, which is write-op. By holding readLock (above) we already ensured
    // nobody _modifies_ page concurrently with us. The only possible contender is another .flush()
    // call -- hence we use 'this' lock to avoid concurrent flushes.

    //'this' lock is mostly uncontended: other than here, it guards .regionModified() --
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

      storageHandle.flushBytes(sliceToSave, offsetInFile() + minOffsetModified);
      storageHandle.pageBecomeClean();
      modifiedRegionPacked = EMPTY_MODIFIED_REGION;
    }
  }

  //@GuardedBy(pageLock.writeLock)
  @Override
  public void regionModified(final int startOffsetModified,
                             final int length) {
    assert contentProtectingLock.writeLock().isHeldByCurrentThread() : "writeLock must be held while calling this method";

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

    storageHandle.modifiedRegionUpdated(
      offsetInFile() + startOffsetModified,
      length
    );
    if (modifiedRegionOld == 0 && modifiedRegionNew != 0) {
      storageHandle.pageBecomeDirty();
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
    contentProtectingLock.readLock().lock();
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
      contentProtectingLock.readLock().unlock();
    }
  }

  @Override
  public <OUT, E extends Exception> OUT write(final int startOffsetOnPage,
                                              final int length,
                                              final ThrowableNotNullFunction<ByteBuffer, OUT, E> writer) throws E {
    contentProtectingLock.writeLock().lock();
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
      contentProtectingLock.writeLock().unlock();
    }
  }

  @Override
  public String toString() {
    final long modifiedRegion = this.modifiedRegionPacked;
    final int minOffset = unpackMinOffsetModified(modifiedRegion);
    final int maxOffsetExclusive = unpackMaxOffsetModifiedExclusive(modifiedRegion);
    return super.toString()
           + ", dirtyRegion: [" + minOffset + ".." + maxOffsetExclusive + ") ";
  }
}
