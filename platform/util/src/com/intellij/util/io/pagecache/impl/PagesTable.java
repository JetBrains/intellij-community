// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache.impl;

import com.intellij.openapi.util.IntRef;
import com.intellij.util.io.ClosedStorageException;
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
import java.util.concurrent.locks.ReentrantReadWriteLock;
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
public class PagesTable {
  private static final double GROWTH_FACTOR = 1.5;
  private static final double SHRINK_FACTOR = 2;

  private static final int MIN_TABLE_SIZE = 16;

  public static final float DEFAULT_LOAD_FACTOR = 0.4f;

  private final float loadFactor;

  /** Pages in pages array: count all !null entries, including tombstones */
  //@GuardedBy(pagesLock.writeLock)
  private int pagesCount = 0;

  /**
   * Content: Page{state: NOT_READY | READY_FOR_USE | TO_UNMAP}.
   * Reads are non-blocking, writes must be guarded by .pagesLock
   */
  @NotNull
  private volatile AtomicReferenceArray<PageImpl> pages;

  //TODO RC: seems like we don't need R-W lock here -- simple lock is enough, even intrinsic one
  //         The only method we use readLock is .flushAll(), and it looks like it is flawed anyway
  private transient final ReentrantReadWriteLock pagesLock = new ReentrantReadWriteLock();


  public PagesTable(final int initialSize) {
    this(initialSize, DEFAULT_LOAD_FACTOR);
  }

  public PagesTable(final int initialSize,
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
  public PageImpl lookupOrCreate(final int pageIndex,
                                 final @NotNull IntFunction<PageImpl> uninitializedPageFactory,
                                 final @NotNull PageContentLoader pageContentLoader) throws IOException {
    final PageImpl page = findPageOrInsertionIndex(this.pages, pageIndex, /*insertionIndexRef: */null);
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

  /** Shrink table if alivePagesCount is too small for current size. */
  public boolean shrinkIfNeeded(final int alivePagesCount) {
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

  public AtomicReferenceArray<PageImpl> pages() {
    return pages;
  }

  public ReentrantReadWriteLock pagesLock() {
    return pagesLock;
  }


  @NotNull
  private PageImpl insertNewPage(final int pageIndex,
                                 final IntFunction<PageImpl> uninitializedPageFactory,
                                 final PageContentLoader pageContentLoader) throws IOException {

    //Don't try to be lock-free on updates, just avoid holding the _global_ lock during IO:
    // 1) put blankPage under the pagesLock,
    // 2) release the pagesLock,
    // 3) load page outside the pagesLock, under per-page lock
    final IntRef insertionIndexRef = new IntRef();
    final PageImpl blankPage;
    pagesLock.writeLock().lock();
    try {
      final PageImpl alreadyInsertedPage = findPageOrInsertionIndex(this.pages, pageIndex, insertionIndexRef);
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

          //RC: we need not only enlargement, but also shrinking! PageTable grows big temporarily,
          //    while we read the file intensively -- but after that period the table should shrink.
          //    Contrary to the enlargement, shrinking is better to be implemented by maintenance
          //    thread, in background. This is because a lot of entries in the enlarged table
          //    would be tombstones (pages already reclaimed, but entries remain) and maintaining
          //    the count of those tombstones is not convenient -- Page status changes are generally
          //    detached from PageTable logic, and better be kept that way. But the maintenance thread
          //    regularly scans all the pages anyway, so it could easily count (tombstones vs alive)
          //    entries, and trigger shrinking if there are <30-40% entries are alive.
        }
      }
      else {
        // (page == null && insertionIndex < 0)
        // => no space remains in table
        //    => this is unexpected, since we resize table well in advance
        throw new AssertionError("Bug: table[len:" + pages.length() + "] is full, but only " + pagesCount + " entries are in." + pages);
      }
    }
    finally {
      pagesLock.writeLock().unlock();
    }

    blankPage.pageLock().writeLock().lock();
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
      blankPage.pageLock().writeLock().unlock();
    }
  }
  //@GuardedBy(pagesLock.writeLock)

  private void rehashToSize(final int newPagesSize) {
    assert pagesLock.writeLock().isHeldByCurrentThread() : "Must hold writeLock while rehashing";

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
   */
  //@GuardedBy(pagesLock.writeLock)
  private static int rehashWithoutTombstones(final @NotNull AtomicReferenceArray<PageImpl> sourcePages,
                                             final @NotNull AtomicReferenceArray<PageImpl> targetPages) {
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
        //either targetPages is too small to fit, or code bug in hashing logic
        throw new AssertionError("Insertion index must be found for Page[#" + pageIndex + "] during rehash. " +
                                 " source.length: " + sourcePages.length() + " target.length: " + targetPages.length() +
                                 "\nsource: " + sourcePages +
                                 "\ntarget: " + targetPages);
      }

      targetPages.set(insertionIndex, page);
      pagesCopied++;
    }
    return pagesCopied;
  }

  @Nullable
  private static PageImpl findPageOrInsertionIndex(final @NotNull AtomicReferenceArray<PageImpl> pages,
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
    final Int2IntOpenHashMap histo = new Int2IntOpenHashMap();
    for (int i = 0; i < pages.length(); i++) {
      final PageImpl page = pages.get(i);
      if (page != null) {
        final int probingLength = probingSequenceLengthFor(pages, page.pageIndex());
        histo.addTo(probingLength, 1);
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
}
