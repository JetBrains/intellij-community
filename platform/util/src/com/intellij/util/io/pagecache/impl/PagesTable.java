// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache.impl;

import com.intellij.openapi.util.IntRef;
import com.intellij.util.io.pagecache.Page;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntFunction;

import static java.util.stream.Collectors.joining;

/**
 * Open-addressing hashmap specialized for {@link PageImpl} objects. Class is internal for file page
 * cache implementation, so even though class itself and a lot of its methods are public, they are
 * all intended to be used only by the file page cache machinery.
 *
 * @see PageImpl
 * @see com.intellij.util.io.FilePageCacheLockFree
 */
@ApiStatus.Internal
public final class PagesTable {
  private static final double GROWTH_FACTOR = 1.5;
  private static final double SHRINK_FACTOR = 2;

  private static final int MIN_TABLE_SIZE = 16;

  public static final float DEFAULT_LOAD_FACTOR = 0.4f;

  private final float loadFactor;

  /** Pages in pages array: count all !null entries, including tombstones */
  //@GuardedBy(pagesLock)
  private int pagesCount = 0;

  /**
   * Reads are non-blocking, writes must be guarded by .pagesLock
   */
  private volatile @NotNull AtomicReferenceArray<PageImpl> pages;

  private final transient ReentrantLock pagesLock = new ReentrantLock();


  public PagesTable(final int initialSize) {
    this(initialSize, DEFAULT_LOAD_FACTOR);
  }

  public PagesTable(final int initialSize,
                    final float loadFactor) {
    this.loadFactor = loadFactor;
    final int size = Math.max(MIN_TABLE_SIZE, (int)(initialSize / this.loadFactor));
    pages = new AtomicReferenceArray<>(size);
  }

  public @Nullable Page lookupIfExist(final int pageIndex) {
    return findPageOrInsertionIndex(this.pages, pageIndex, /*insertionIndexRef: */ null);
  }

  public @NotNull PageImpl lookupOrCreate(final int pageIndex,
                                          final @NotNull IntFunction<PageImpl> uninitializedPageFactory) throws IOException {
    final PageImpl page = findPageOrInsertionIndex(this.pages, pageIndex, /*insertionIndexRef: */null);
    if (page != null) {
      return page;
    }
    return insertNewPage(pageIndex, uninitializedPageFactory);
  }

  public void flushAll() throws IOException {
    //RC: it is hard to define semantics of .flush() implementation in a concurrent environment.
    //    I.e. new pages could be created and inserted in parallel, and those pages will or will
    //    not be flushed, depending on there in .pages array they are inserted, and there the
    //    iteration index is at the moment of insertion. And pages just flushed could become
    //    dirty again even before the loop is finished.
    //    But I see no simple way to fix it, apart from returning to global lock protecting all
    //    writes -- which is exactly what we're escaping from by moving to concurrent implementation.
    for (int i = 0; i < pages.length(); i++) {
      final PageImpl page = pages.get(i);
      if (page != null && page.isDirty()) {
        page.flush();
      }
    }
  }

  /** Shrink table if alivePagesCount is too small for current size. */
  public boolean shrinkIfNeeded(final int alivePagesCount) {
    final int expectedTableSize = (int)Math.ceil(alivePagesCount / loadFactor);
    if (expectedTableSize >= MIN_TABLE_SIZE
        && expectedTableSize * SHRINK_FACTOR < pages.length()) {
      pagesLock.lock();
      try {
        try {
          rehashToSize(expectedTableSize);
        }
        catch (NoFreeSpaceException e) {
          //RC: table content could change between alivePagesCount calculation, and actual rehash under
          //    the lock: more pages could be inserted into a table, so alivePagesCount is an underestimation.
          //    It could be so huge an underestimation that shrinking is not appropriate at all -- e.g.
          //    there will be not enough slots in a table, if shrunk. We deal with it speculatively: try
          //    to rehashToSize(), and cancel resize if NoFreeSpaceException is thrown:
          return false;
        }
        return true;
      }
      finally {
        pagesLock.unlock();
      }
    }
    return false;
  }

  public AtomicReferenceArray<PageImpl> pages() {
    return pages;
  }

  public ReentrantLock pagesLock() {
    return pagesLock;
  }


  private @NotNull PageImpl insertNewPage(final int pageIndex,
                                          final IntFunction<PageImpl> uninitializedPageFactory) {

    //Don't try to be lock-free on updates, just avoid holding the _global_ lock during IO:
    // 1) put blankPage under the pagesLock,
    // 2) release the pagesLock,
    // 3) load page outside the pagesLock, under per-page lock
    final IntRef insertionIndexRef = new IntRef();
    final PageImpl blankPage;
    pagesLock.lock();
    try {
      final PageImpl alreadyInsertedPage = findPageOrInsertionIndex(pages, pageIndex, insertionIndexRef);
      if (alreadyInsertedPage != null) {
        //race: somebody inserted the page between lock-free speculative check and locked re-check
        return alreadyInsertedPage;
      }

      if (insertionIndexRef.get() < 0) {
        // (page == null && insertionIndex < 0) => no space remains in the table
        final int newTableSize = (int)Math.ceil((pagesCount / loadFactor) * GROWTH_FACTOR);
        rehashToSize(newTableSize);

        final PageImpl mustBeNull = findPageOrInsertionIndex(pages, pageIndex, insertionIndexRef);
        if (mustBeNull != null) {
          throw new AssertionError(
            "Bug: first lookup(pageIndex: " + pageIndex + ") founds nothing, same search after resize must find nothing too " + pages);
        }
        if (insertionIndexRef.get() < 0) {
          throw new AssertionError("Bug: table just resized {length: " + pages.length() + ", pages: " + pagesCount + "} => " +
                                   "there must be a free slot for pageIndex: " + pageIndex);
        }
      }

      blankPage = uninitializedPageFactory.apply(pageIndex);

      final int insertionIndex = insertionIndexRef.get();
      pages.set(insertionIndex, blankPage);
      pagesCount++;

      if (pagesCount > pages.length() * loadFactor) {
        final int newTableSize = (int)Math.ceil((pagesCount / loadFactor) * GROWTH_FACTOR);
        rehashToSize(newTableSize);

        //RC: page table need not only enlargement, but also shrinking. PageTable grows big
        //    temporarily, while we read the file intensively -- but after that period the
        //    table should shrink. Contrary to the enlargement, shrinking is implemented by
        //    maintenance thread, in the background. This is because a lot of entries in the
        //    enlarged table would be tombstones (pages already reclaimed, but entries remain)
        //    and maintaining the count of those tombstones is not convenient -- Page status
        //    changes (...->tombstone) are generally detached from PageTable logic, and better
        //    be kept that way. But the maintenance thread regularly scans all the pages anyway,
        //    so it could easily count (tombstones vs alive) entries, and trigger shrinking if
        //    there are <30-40% entries are alive.
      }
    }
    finally {
      pagesLock.unlock();
    }

    return blankPage;


    //blankPage.pageLock().writeLock().lock();
    //try {
    //  if (blankPage.isTombstone()) {
    //    throw new ClosedStorageException("Storage is already closed");
    //  }
    //  if (!blankPage.isNotReadyYet()) {
    //    //possible if short-circuit (NOT_READY_YET->TOMBSTONE) transition of storage close happens
    //    throw new AssertionError("Page must be {NOT_READY_YET, TOMBSTONE}, but " + blankPage);
    //  }
    //  blankPage.prepareForUse(pageContentLoader);
    //  if (blankPage.isNotReadyYet()) {
    //    //RC: Page state could be any of {USABLE, ABOUT_TO_UNMAP, TOMBSTONE} here -- but not NOT_READY_YET.
    //    //    USABLE is the obvious case, but {ABOUT_TO_UNMAP, TOMBSTONE} could happen if new page
    //    //    allocation races with storage closing: in such a scenario the page could be entombed async-ly
    //    //    immediately after .prepareForUse() made it USABLE -- nothing prevents it, since page
    //    //    has usageCount=0 still.
    //    throw new AssertionError("Page must be {USABLE, ABOUT_TO_UNMAP, TOMBSTONE}, but " + blankPage);
    //  }
    //  if (blankPage.isDirty()) {
    //    throw new AssertionError("Page must NOT be dirty just after .pageLoader: " + blankPage);
    //  }
    //  return blankPage;
    //}
    //finally {
    //  blankPage.pageLock().writeLock().unlock();
    //}
  }

  //@GuardedBy(pagesLock)
  private void rehashToSize(final int newPagesSize) throws NoFreeSpaceException {
    assert pagesLock.isHeldByCurrentThread() : "Must hold pagesLock while rehashing";

    final AtomicReferenceArray<PageImpl> newPages = new AtomicReferenceArray<>(newPagesSize);
    final int pagesCopied = rehashWithoutTombstones(pages, newPages);

    this.pagesCount = pagesCopied;//tombstones were removed during rehash
    this.pages = newPages;
  }

  /**
   * Method hashes pages from sourcePages into targetPages, using {@link #findPageOrInsertionIndex(AtomicReferenceArray, int, IntRef)}.
   * Pages with [state:TOMBSTONE] are skipped during the copy.
   *
   * @return number of pages copied
   * @throws NoFreeSpaceException if targetPages is not big enough to fit all !tombstone pages from sourcePages
   */
  //@GuardedBy(pagesLock)
  private static int rehashWithoutTombstones(final @NotNull AtomicReferenceArray<PageImpl> sourcePages,
                                             final @NotNull AtomicReferenceArray<PageImpl> targetPages) throws NoFreeSpaceException {
    final IntRef insertionIndexRef = new IntRef();
    int pagesCopied = 0;
    for (int i = 0; i < sourcePages.length(); i++) {
      final PageImpl page = sourcePages.get(i);
      if (page == null) {
        continue;
      }
      else if (page.isTombstone()) {
        continue;
      }
      final int pageIndex = page.pageIndex();
      final PageImpl pageMustNotBeFound = findPageOrInsertionIndex(targetPages, pageIndex, insertionIndexRef);
      final int insertionIndex = insertionIndexRef.get();

      if (pageMustNotBeFound != null) {
        throw new AssertionError("Page[#" + pageIndex + "] is copying now -- can't be already in .newPages! " +
                                 "\nsource: " + sourcePages +
                                 "\ntarget: " + targetPages);
      }
      if (insertionIndex < 0) {
        if (pagesCopied == targetPages.length()) {
          //either targetPages is too small to fit, or code bug in hashing logic
          throw new NoFreeSpaceException("Not enough space in targetPages(length: " + targetPages.length() + "): " +
                                         "sourcePages(length: " + sourcePages.length() + ") contains > " + pagesCopied +
                                         " !tombstone pages. \nsource: " + sourcePages + "\ntarget: " + targetPages);
        }
        else {
          throw new AssertionError("Bug: insertion index must be found for Page[#" + pageIndex + "] during rehash. " +
                                   " source.length: " + sourcePages.length() + " target.length: " + targetPages.length() +
                                   "\nsource: " + sourcePages +
                                   "\ntarget: " + targetPages);
        }
      }

      targetPages.set(insertionIndex, page);
      pagesCopied++;
    }
    return pagesCopied;
  }

  private static @Nullable PageImpl findPageOrInsertionIndex(final @NotNull AtomicReferenceArray<PageImpl> pages,
                                                             final int pageIndex,
                                                             final @Nullable IntRef insertionIndexRef) {
    final int length = pages.length();
    final int initialSlotIndex = hash(pageIndex) % length;
    final int probeStep = 1; // = linear probing

    int firstTombstoneIndex = -1;
    for (int slotIndex = initialSlotIndex, probeNo = 0;
         probeNo < length;
         probeNo++) {
      final PageImpl page = pages.get(slotIndex);

      if (page == null) {
        //end of the probing sequence reached: no such page
        if (insertionIndexRef != null) {
          final int insertionIndex = firstTombstoneIndex >= 0 ? firstTombstoneIndex : slotIndex;
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
          firstTombstoneIndex = slotIndex;
        }
      }
      else if (page.pageIndex() == pageIndex) {
        if (insertionIndexRef != null) {
          insertionIndexRef.set(slotIndex);
        }
        return page;
      }

      slotIndex = (slotIndex + probeStep) % length;
    }

    //no page found AND no space to insert -> need resize
    if (insertionIndexRef != null) {
      insertionIndexRef.set(-1);
    }
    return null;
  }

  private static int hash(final int pageIndex) {
    return Math.abs(HashCommon.mix(pageIndex));
  }


  public String probeLengthsHistogram() {
    final Int2IntMap histo = collectProbeLengthsHistogram();
    final int sum = histo.values().intStream().sum();
    return histo.keySet().intStream()
      .sorted()
      .mapToObj(len -> String.format("%3d: %4d (%4.1f%%)", len, histo.get(len), histo.get(len) * 100.0 / sum))
      .collect(joining("\n"));
  }

  @VisibleForTesting
  public Int2IntMap collectProbeLengthsHistogram() {
    final Int2IntMap histo = new Int2IntOpenHashMap();
    for (int i = 0; i < pages.length(); i++) {
      final PageImpl page = pages.get(i);
      if (page != null) {
        final int probingLength = probingSequenceLengthFor(pages, page.pageIndex());
        histo.mergeInt(probingLength, 1, Integer::sum);
      }
    }
    return histo;
  }

  private static int probingSequenceLengthFor(final @NotNull AtomicReferenceArray<PageImpl> pages,
                                              final int pageIndex) {
    //RC: this is a reduced copy of findPageOrInsertionIndex() -> better adjust
    //    findPageOrInsertionIndex() to return probes count via additional out-param.
    final int length = pages.length();
    final int initialSlotIndex = hash(pageIndex) % length;
    final int probeStep = 1; //=linear probing

    for (int slotIndex = initialSlotIndex, probeNo = 0;
         probeNo < length;
         probeNo++) {
      final PageImpl page = pages.get(slotIndex);

      if (page == null) {
        return probeNo;
      }

      if (page.isTombstone()) {
        //Tombstone: page was removed -> look up further, but remember the position
      }
      else if (page.pageIndex() == pageIndex) {
        return probeNo;
      }

      slotIndex = (slotIndex + probeStep) % length;
    }

    //no page found
    return pages.length();
  }

  private static final class NoFreeSpaceException extends IllegalStateException {
    private NoFreeSpaceException(final String message) { super(message); }
  }
}
