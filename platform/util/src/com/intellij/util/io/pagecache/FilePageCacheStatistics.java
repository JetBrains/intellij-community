// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Used by {@link com.intellij.util.io.FilePageCacheLockFree} to gather inside statistics
 */
public class FilePageCacheStatistics {
  private final AtomicLong totalNativeBytesAllocated = new AtomicLong();
  private final AtomicLong totalNativeBytesReclaimed = new AtomicLong();
  private final AtomicLong totalHeapBytesAllocated = new AtomicLong();
  private final AtomicLong totalHeapBytesReclaimed = new AtomicLong();

  private final AtomicInteger totalPagesAllocated = new AtomicInteger();
  private final AtomicInteger totalPagesReclaimed = new AtomicInteger();

  private final AtomicLong totalBytesRead = new AtomicLong();
  //TODO RC: count bytes really saved during flush (i.e. accounting for actual dirtyRegion)
  //private final AtomicLong totalBytesWritten = new AtomicLong();

  private final AtomicLong totalPagesFlushed = new AtomicLong();

  /**
   * Total pages requested from {@link com.intellij.util.io.PagedFileStorageLockFree#pageByOffset(long, boolean)}
   * methods
   */
  private final AtomicLong totalPagesRequested = new AtomicLong();
  /**
   * Total pages bytes requested from {@link com.intellij.util.io.PagedFileStorageLockFree#pageByOffset(long, boolean)}
   * methods
   */
  private final AtomicLong totalPagesBytesRequested = new AtomicLong();

  //modified by housekeeper thread only, hence not atomic but just volatile:
  private volatile long housekeeperTurnDone = 0;
  private volatile long housekeeperTurnSkipped = 0;

  private volatile int totalClosedStoragesReclaimed;


  public void pageAllocatedDirect(final int size) {
    totalNativeBytesAllocated.addAndGet(size);
    totalPagesAllocated.incrementAndGet();
    //TODO RC: this is not precise, since page could be loaded partially (i.e. at EOF)
    totalBytesRead.addAndGet(size);
  }

  public void pageAllocatedHeap(final int size) {
    totalHeapBytesAllocated.addAndGet(size);
    totalPagesAllocated.incrementAndGet();
    //TODO RC: this is not precise, since page could be loaded partially (i.e. at EOF)
    totalBytesRead.addAndGet(size);
  }

  public void pageReclaimedNative(final int pageSize) {
    totalNativeBytesReclaimed.addAndGet(pageSize);
    totalPagesReclaimed.incrementAndGet();
  }

  public void pageReclaimedHeap(final int pageSize) {
    totalHeapBytesReclaimed.addAndGet(pageSize);
    totalPagesReclaimed.incrementAndGet();
  }


  public void cacheMaintenanceTurnDone() {
    housekeeperTurnDone++;
  }

  public void cacheMaintenanceTurnSkipped() {
    housekeeperTurnSkipped++;
  }

  public void closedStoragesReclaimed(final int reclaimed) {
    totalClosedStoragesReclaimed += reclaimed;
  }

  public void pagesFlushed(final int flushed) {
    totalPagesFlushed.addAndGet(flushed);
  }

  public void pageQueried(final int pageSize) {
    totalPagesBytesRequested.addAndGet(pageSize);
    totalPagesRequested.incrementAndGet();
  }

  @Override
  public String toString() {
    return "Statistics[" +

           "housekeeperTurns: {done: " + housekeeperTurnDone + ", skipped: " + housekeeperTurnSkipped + "}, " +

           "nativeBytes: " +
           "{allocated: " + totalNativeBytesAllocated + ", reclaimed: " + totalNativeBytesReclaimed + "}, " +
           "heapBytes: " +
           "{allocated: " + totalHeapBytesAllocated + ", reclaimed: " + totalHeapBytesReclaimed + "}, " +
           "pages: " +
           "{requested: " + totalPagesRequested +
           ", allocated: " + totalPagesAllocated +
           ", flushed: " + totalPagesFlushed +
           ", reclaimed: " + totalPagesReclaimed + "}, " +

           "totalBytesRequested: " + totalPagesBytesRequested +
           ", totalBytesRead: " + totalBytesRead +
           //", totalBytesWritten=" + totalBytesWritten +
           ", closedStoragesReclaimed: " + totalClosedStoragesReclaimed +
           ']';
  }
}
