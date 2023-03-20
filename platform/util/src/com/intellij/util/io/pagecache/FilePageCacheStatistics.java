// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache;

import com.intellij.util.io.FilePageCacheLockFree;
import com.intellij.util.io.IOUtil;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Used by {@link FilePageCacheLockFree} to gather inside statistics
 */
public class FilePageCacheStatistics {
  private static final boolean MEASURE_TIMESTAMPS = true;

  private final AtomicLong totalNativeBytesAllocated = new AtomicLong();
  private final AtomicLong totalNativeBytesReclaimed = new AtomicLong();
  private final AtomicLong totalHeapBytesAllocated = new AtomicLong();
  private final AtomicLong totalHeapBytesReclaimed = new AtomicLong();

  private final AtomicInteger totalPagesAllocated = new AtomicInteger();
  private final AtomicInteger totalPagesReclaimed = new AtomicInteger();
  private final AtomicInteger totalPagesHandedOver = new AtomicInteger();

  private final AtomicLong totalBytesRead = new AtomicLong();
  private final AtomicLong totalBytesWritten = new AtomicLong();

  private final AtomicLong totalPagesWritten = new AtomicLong();

  /**
   * Total pages requested from {@link com.intellij.util.io.PagedFileStorageLockFree#pageByOffset(long, boolean)}
   * methods
   */
  private final AtomicLong totalPagesRequested = new AtomicLong();
  /**
   * Total pages bytes requested from {@link com.intellij.util.io.PagedFileStorageLockFree#pageByOffset(long, boolean)}
   * methods
   */
  private final AtomicLong totalBytesRequested = new AtomicLong();

  private final AtomicLong totalPagesRequestsNs = new AtomicLong();
  private final AtomicLong totalPagesReadNs = new AtomicLong();
  private final AtomicLong totalPagesWriteNs = new AtomicLong();


  //modified by housekeeper thread only, hence not atomic but just volatile:
  private volatile long housekeeperTurnDone = 0;
  private volatile long housekeeperTurnSkipped = 0;

  private volatile int totalClosedStoragesReclaimed;
  //MAYBE RC: totalStorageRegistered?
  //TODO  RC: totalTimeTakenByLoadUs?
  //TODO  RC: totalTimeTakenByWriteUs?
  //TODO  RC: totalTimeTakenByPageGetUs?

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
    totalBytesRead.addAndGet(bytesRead);
    if (MEASURE_TIMESTAMPS) {
      totalPagesReadNs.addAndGet(System.nanoTime() - readStartedAtNs);
    }
  }

  public void pageWritten(final int bytesWritten,
                          final long writeStartedAtNs) {
    totalPagesWritten.incrementAndGet();
    totalBytesWritten.addAndGet(bytesWritten);
    if (MEASURE_TIMESTAMPS) {
      totalPagesWriteNs.addAndGet(System.nanoTime() - writeStartedAtNs);
    }
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

  //==================== getters:

  public long totalNativeBytesAllocated() { return totalNativeBytesAllocated.get(); }

  public long totalNativeBytesReclaimed() { return totalNativeBytesReclaimed.get(); }

  public long totalHeapBytesAllocated() { return totalHeapBytesAllocated.get(); }

  public long totalHeapBytesReclaimed() { return totalHeapBytesReclaimed.get(); }

  public int totalPagesAllocated() { return totalPagesAllocated.get(); }

  public int totalPagesReclaimed() { return totalPagesReclaimed.get(); }

  public long totalBytesRead() { return totalBytesRead.get(); }

  public long totalBytesWritten() { return totalBytesWritten.get(); }

  public long totalPagesWritten() { return totalPagesWritten.get(); }

  public long totalPagesRequested() { return totalPagesRequested.get(); }

  public long totalBytesRequested() { return totalBytesRequested.get(); }

  public long totalPagesRequestsNs() { return totalPagesRequestsNs.get(); }

  public long totalPagesReadNs() { return totalPagesReadNs.get(); }

  public long totalPagesWriteNs() { return totalPagesWriteNs.get(); }


  public long housekeeperTurnDone() {
    return housekeeperTurnDone;
  }

  public long housekeeperTurnSkipped() {
    return housekeeperTurnSkipped;
  }

  public int totalClosedStoragesReclaimed() {
    return totalClosedStoragesReclaimed;
  }


  @Override
  public String toString() {
    return "Statistics[" +
           "pages: " +
           "{requested: " + totalPagesRequested + ", allocated: " + totalPagesAllocated +
           ", flushed: " + totalPagesWritten + ", reclaimed: " + totalPagesReclaimed + "}, " +

           "nativeBytes: " +
           "{allocated: " + totalNativeBytesAllocated + ", reclaimed: " + totalNativeBytesReclaimed + "}, " +
           "heapBytes: " +
           "{allocated: " + totalHeapBytesAllocated + ", reclaimed: " + totalHeapBytesReclaimed + "}, " +

           "pages handed over: " + totalPagesHandedOver + ", " +

           "bytes: {requested: " + totalBytesRequested + ", read: " + totalBytesRead + ", written=" + totalBytesWritten + "}, " +

           "housekeeperTurns: {done: " + housekeeperTurnDone + ", skipped: " + housekeeperTurnSkipped + "}, " +
           "closedStoragesReclaimed: " + totalClosedStoragesReclaimed +
           ']';
  }

  public String toPrettyString() {
    final long totalBytesAllocated = totalNativeBytesAllocated.get() + totalHeapBytesAllocated.get();
    return String.format(
      "Statistics: {\n" +
      " pages: {\n" +
      "   requested:   %s\n" +
      "   allocated:   %s (~%2.1f%% of requested)\n" +
      "   written:     %s (~%2.1f%% of allocated)\n" +
      "   reclaimed:   %s (~%2.1f%% of allocated)\n" +
      "   handed over: %s (~%2.1f%% of allocated)\n" +
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
      " }\n" +

      " heap memory: {\n" +
      "   allocated: %s b (~%.1f%% of total)\n" +
      "   reclaimed: %s b (~%.1f%% of allocated)\n" +
      " }\n" +

      " housekeeperTurns: {done: %d, skipped: %d}\n" +
      " closedStoragesReclaimed: %d\n" +
      "}",

      totalPagesRequested,
      totalPagesAllocated, (totalPagesAllocated.get() * 100.0 / totalPagesRequested.get()),
      totalPagesWritten, (totalPagesWritten.get() * 100.0 / totalPagesAllocated.get()),
      totalPagesReclaimed, (totalPagesReclaimed.get() * 100.0 / totalPagesAllocated.get()),
      totalPagesHandedOver, (totalPagesHandedOver.get() * 100.0 / totalPagesAllocated.get()),

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

      totalHeapBytesAllocated, (totalHeapBytesAllocated.get() * 100.0 / totalBytesAllocated),
      totalHeapBytesReclaimed, (totalHeapBytesReclaimed.get() * 100.0 / totalHeapBytesAllocated.get()),


      housekeeperTurnDone, housekeeperTurnSkipped,

      totalClosedStoragesReclaimed
    );
  }
}
