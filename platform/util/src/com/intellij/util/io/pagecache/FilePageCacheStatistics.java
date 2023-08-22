// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache;

import com.intellij.util.io.FilePageCacheLockFree;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.PagedFileStorageWithRWLockedPageContent;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Used by {@link FilePageCacheLockFree} to gather inside statistics
 */
public final class FilePageCacheStatistics {
  private static final boolean MEASURE_TIMESTAMPS = true;

  private final AtomicLong totalNativeBytesAllocated = new AtomicLong();
  private final AtomicLong totalNativeBytesReclaimed = new AtomicLong();
  private final AtomicLong totalHeapBytesAllocated = new AtomicLong();
  private final AtomicLong totalHeapBytesReclaimed = new AtomicLong();

  private volatile long nativeBytesCurrentlyUsed = 0;
  private volatile long heapBytesCurrentlyUsed = 0;

  private final AtomicInteger totalPagesAllocated = new AtomicInteger();
  private final AtomicInteger totalPagesReclaimed = new AtomicInteger();
  private final AtomicInteger totalPagesHandedOver = new AtomicInteger();

  /** Total bytes read from disk (since cache creation) */
  private final AtomicLong totalBytesRead = new AtomicLong();
  /** Total bytes written on disk during pages flush (since cache creation) */
  private final AtomicLong totalBytesWritten = new AtomicLong();
  /** Total pages written on disk == i.e. total number of .flush calls that actually write >1 bytes */
  private final AtomicLong totalPagesWritten = new AtomicLong();

  /**
   * Total pages requested from {@link PagedFileStorageWithRWLockedPageContent#pageByOffset(long, boolean)}
   * methods
   */
  private final AtomicLong totalPagesRequested = new AtomicLong();
  /**
   * Cumulative size of all pages requested from {@link PagedFileStorageWithRWLockedPageContent#pageByOffset(long, boolean)}
   * methods
   */
  private final AtomicLong totalBytesRequested = new AtomicLong();

  private final AtomicLong totalPagesRequestsNs = new AtomicLong();
  private final AtomicLong totalPagesReadNs = new AtomicLong();
  private final AtomicLong totalPagesWriteNs = new AtomicLong();

  /** How many times page-allocating thread was forced to wait so housekeeper thread could keep up */
  private final AtomicInteger pageAllocationsWaited = new AtomicInteger();


  //modified by housekeeper thread only, hence not atomic but just volatile:
  private volatile long housekeeperTurnsDone = 0;
  private volatile long housekeeperTurnsSkipped = 0;
  private volatile long housekeeperTotalTimeNs = 0;

  private volatile int totalClosedStoragesReclaimed;

  //MAYBE RC: totalStorageRegistered?


  public long startTimestampNs() {
    if (MEASURE_TIMESTAMPS) {
      return System.nanoTime();
    }
    else {
      return 0L;
    }
  }


  public void pageAllocatedNative(final int pageSize) {
    totalNativeBytesAllocated.addAndGet(pageSize);
    totalPagesAllocated.incrementAndGet();
  }

  public void pageAllocatedHeap(final int pageSize) {
    totalHeapBytesAllocated.addAndGet(pageSize);
    totalPagesAllocated.incrementAndGet();
  }

  public void pageReclaimedNative(final int pageSize) {
    totalNativeBytesReclaimed.addAndGet(pageSize);
    totalPagesReclaimed.incrementAndGet();
  }

  public void pageReclaimedHeap(final int pageSize) {
    totalHeapBytesReclaimed.addAndGet(pageSize);
    totalPagesReclaimed.incrementAndGet();
  }

  public void pageReclaimedByHandover(final int pageSize,
                                      final boolean direct) {
    totalPagesHandedOver.incrementAndGet();
    if (direct) {
      pageReclaimedNative(pageSize);
      pageAllocatedNative(pageSize);
    }
    else {
      pageReclaimedHeap(pageSize);
      pageAllocatedHeap(pageSize);
    }
  }


  public void pageRequested(final int pageSize,
                            final long requestStartedAtNs) {
    totalBytesRequested.addAndGet(pageSize);
    totalPagesRequested.incrementAndGet();
    if (MEASURE_TIMESTAMPS) {
      totalPagesRequestsNs.addAndGet(System.nanoTime() - requestStartedAtNs);
    }
  }

  public void pageRead(final int bytesRead,
                       final long readStartedAtNs) {
    assert bytesRead >= 0 : "bytesRead must be >=0, " + bytesRead;
    totalBytesRead.addAndGet(bytesRead);
    if (MEASURE_TIMESTAMPS) {
      totalPagesReadNs.addAndGet(System.nanoTime() - readStartedAtNs);
    }
  }

  public void pageWritten(final int bytesWritten,
                          final long writeStartedAtNs) {
    assert bytesWritten >= 0 : "bytesWritten must be >=0, " + bytesWritten;
    totalPagesWritten.incrementAndGet();
    totalBytesWritten.addAndGet(bytesWritten);
    if (MEASURE_TIMESTAMPS) {
      totalPagesWriteNs.addAndGet(System.nanoTime() - writeStartedAtNs);
    }
  }

  public void pageAllocationWaited() {
    pageAllocationsWaited.incrementAndGet();
  }


  public void heapBytesCurrentlyUsed(long heapBytesCurrentlyUsed) {
    this.heapBytesCurrentlyUsed = heapBytesCurrentlyUsed;
  }

  public void nativeBytesCurrentlyUsed(long nativeBytesCurrentlyUsed) {
    this.nativeBytesCurrentlyUsed = nativeBytesCurrentlyUsed;
  }


  public void cacheMaintenanceTurnDone(long timeSpentNs) {
    //noinspection NonAtomicOperationOnVolatileField
    housekeeperTurnsDone++;
    //noinspection NonAtomicOperationOnVolatileField
    housekeeperTotalTimeNs += timeSpentNs;
  }

  public void cacheMaintenanceTurnSkipped(long timeSpentNs) {
    //noinspection NonAtomicOperationOnVolatileField
    housekeeperTurnsSkipped++;
    //noinspection NonAtomicOperationOnVolatileField
    housekeeperTotalTimeNs += timeSpentNs;
  }

  public void closedStoragesReclaimed(final int reclaimed) {
    //noinspection NonAtomicOperationOnVolatileField
    totalClosedStoragesReclaimed += reclaimed;
  }

  //==================== getters:

  public long totalNativeBytesAllocated() { return totalNativeBytesAllocated.get(); }

  public long totalNativeBytesReclaimed() { return totalNativeBytesReclaimed.get(); }

  public long totalHeapBytesAllocated() { return totalHeapBytesAllocated.get(); }

  public long totalHeapBytesReclaimed() { return totalHeapBytesReclaimed.get(); }

  public long heapBytesCurrentlyUsed() {
    return heapBytesCurrentlyUsed;
  }

  public long nativeBytesCurrentlyUsed() {
    return nativeBytesCurrentlyUsed;
  }

  public int totalPagesAllocated() { return totalPagesAllocated.get(); }

  public int totalPagesReclaimed() { return totalPagesReclaimed.get(); }

  public int totalPagesHandedOver() { return totalPagesHandedOver.get(); }

  public int totalPageAllocationsWaited() {
    return pageAllocationsWaited.get();
  }


  public long totalBytesRequested() { return totalBytesRequested.get(); }

  public long totalBytesRead() { return totalBytesRead.get(); }

  public long totalBytesWritten() { return totalBytesWritten.get(); }


  public long totalPagesRequested() { return totalPagesRequested.get(); }

  public long totalPagesWritten() { return totalPagesWritten.get(); }


  public long totalPagesRequests(TimeUnit unit) { return unit.convert(totalPagesRequestsNs.get(), NANOSECONDS); }

  public long totalPagesRead(TimeUnit unit) { return unit.convert(totalPagesReadNs.get(), NANOSECONDS); }

  public long totalPagesWrite(TimeUnit unit) { return unit.convert(totalPagesWriteNs.get(), NANOSECONDS); }


  public long housekeeperTurnsDone() {
    return housekeeperTurnsDone;
  }


  public long housekeeperTimeSpent(TimeUnit unit) {
    return unit.convert(housekeeperTotalTimeNs, NANOSECONDS);
  }

  public long housekeeperTurnsSkipped() {
    return housekeeperTurnsSkipped;
  }


  public int totalClosedStoragesReclaimed() {
    return totalClosedStoragesReclaimed;
  }


  @Override
  public String toString() {
    //@formatter:off
    return "Statistics[" +
           "pages: " +
           "{requested: " + totalPagesRequested + ", allocated: " + totalPagesAllocated +
           ", flushed: " + totalPagesWritten + ", reclaimed: " + totalPagesReclaimed + "}, " +

           "nativeBytes: {" +
           "allocated: " + totalNativeBytesAllocated + ", reclaimed: " + totalNativeBytesReclaimed + ", current: " + nativeBytesCurrentlyUsed +
           "}, " +

           "heapBytes: {" +
           "allocated: " + totalHeapBytesAllocated + ", reclaimed: " + totalHeapBytesReclaimed + ", current: " + heapBytesCurrentlyUsed +
           "}, " +

           "pages handed over: " + totalPagesHandedOver + ", " +
           "pages allocation waited: " + pageAllocationsWaited + ", " +

           "bytes: {requested: " + totalBytesRequested + ", read: " + totalBytesRead + ", written=" + totalBytesWritten + "}, " +

           "housekeeperTurns: {done: " + housekeeperTurnsDone + ", skipped: " + housekeeperTurnsSkipped + "}, " +
           "closedStoragesReclaimed: " + totalClosedStoragesReclaimed +
           ']';
    //@formatter:on
  }

  public String toPrettyString() {
    final long totalBytesAllocated = totalNativeBytesAllocated.get() + totalHeapBytesAllocated.get();
    return String.format(
      "Statistics: {\n" +
      " pages: {\n" +
      "   requested:      %s\n" +
      "   allocated:      %s (~%2.1f%% of requested)\n" +
      "   written:        %s (~%2.1f%% of allocated)\n" +
      "   reclaimed:      %s (~%2.1f%% of allocated)\n" +
      "   handed over:    %s (~%2.1f%% of allocated)\n" +
      "   request waited: %s (~%2.1f%% of requested)\n" +
      " }\n" +

      " total bytes: {\n" +
      "   requested: %s\n" +
      "   read:      %s (~%.1f%% of requested)\n" +
      "   written:   %s\n" +
      " }\n\n" +

      " average durations: {\n" +
      "   page request: %5.1f us (~%.0f MiB/s)\n" +
      "   page load:    %5.1f us (~%.0f MiB/s)\n" +
      "   page write:   %5.1f us (~%.0f MiB/s)\n" +
      " }\n\n" +

      " native memory: {\n" +
      "   allocated: %s b (~%2.1f%% of total)\n" +
      "   reclaimed: %s b (~%2.1f%% of allocated)\n" +
      "   current:   %s b\n" +
      " }\n" +

      " heap memory: {\n" +
      "   allocated: %s b (~%.1f%% of total)\n" +
      "   reclaimed: %s b (~%.1f%% of allocated)\n" +
      "   current:   %s b\n" +
      " }\n" +

      " housekeeperTurns: {done: %d, skipped: %d, total time %d ms}\n" +
      " closedStoragesReclaimed: %d\n" +
      "}",

      totalPagesRequested,
      totalPagesAllocated, (totalPagesAllocated.get() * 100.0 / totalPagesRequested.get()),
      totalPagesWritten, (totalPagesWritten.get() * 100.0 / totalPagesAllocated.get()),
      totalPagesReclaimed, (totalPagesReclaimed.get() * 100.0 / totalPagesAllocated.get()),
      totalPagesHandedOver, (totalPagesHandedOver.get() * 100.0 / totalPagesAllocated.get()),
      pageAllocationsWaited, (pageAllocationsWaited.get() * 100.0 / totalPagesRequested.get()),

      totalBytesRequested,
      totalBytesRead, (totalBytesRead.get() * 100.0 / totalBytesRequested.get()),
      totalBytesWritten,

      totalPagesRequestsNs.get() / 1000.0 / totalPagesRequested.get(),
      totalBytesRequested.get() * 1e9 / IOUtil.MiB / totalPagesRequestsNs.get(),
      totalPagesReadNs.get() / 1000.0 / totalPagesAllocated.get(),
      totalBytesRead.get() * 1e9 / IOUtil.MiB / totalPagesReadNs.get(),
      totalPagesWriteNs.get() / 1000.0 / totalPagesWritten.get(),
      totalBytesWritten.get() * 1e9 / IOUtil.MiB / totalPagesWriteNs.get(),

      totalNativeBytesAllocated, (totalNativeBytesAllocated.get() * 100.0 / totalBytesAllocated),
      totalNativeBytesReclaimed, (totalNativeBytesReclaimed.get() * 100.0 / totalNativeBytesAllocated.get()),
      nativeBytesCurrentlyUsed,

      totalHeapBytesAllocated, (totalHeapBytesAllocated.get() * 100.0 / totalBytesAllocated),
      totalHeapBytesReclaimed, (totalHeapBytesReclaimed.get() * 100.0 / totalHeapBytesAllocated.get()),
      heapBytesCurrentlyUsed,


      housekeeperTurnsDone, housekeeperTurnsSkipped, NANOSECONDS.toMillis(housekeeperTotalTimeNs),

      totalClosedStoragesReclaimed
    );
  }
}
