// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.dev.mmapped;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.ThrottledLogger;
import com.intellij.util.io.ClosedStorageException;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.intellij.util.SystemProperties.getIntProperty;
import static java.nio.ByteOrder.nativeOrder;
import static java.nio.channels.FileChannel.MapMode.READ_WRITE;
import static java.nio.file.StandardOpenOption.*;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Storage over memory-mapped file.
 * Hides most of the peculiarities of mmapped-files.
 * But still very low-level, so use with caution -- or better don't. Better use higher-level components, like
 * {@link com.intellij.openapi.vfs.newvfs.persistent.mapped.MappedFileStorageHelper} or {@link com.intellij.openapi.vfs.newvfs.persistent.dev.FastFileAttributes}
 */
@ApiStatus.Internal
public final class MMappedFileStorage implements Closeable {
  private static final Logger LOG = Logger.getInstance(MMappedFileStorage.class);
  private static final ThrottledLogger THROTTLED_LOG = new ThrottledLogger(LOG, 1000);

  //RC: do we need method like 'unmap', which forcibly unmaps pages, or it is enough to rely on JVM which will
  //    unmap pages eventually, as they are collected by GC?
  //    Forcible unmap allows to 'clean after yourself', but carries a risk of JVM crash if somebody still tries
  //    to access unmapped pages.
  //    So far, my take is: in regular use, it is better to let JVM unmap pages, and not carry the risk of JVM
  //    crash after too eager unmapping. There is no clear benefits for explicit (eager) unmap, that outweigh
  //    risk of JVM crash.
  //MAYBE: but we _could_ provide 'unmap' as a dedicated method, not as a part of .close() -- so it
  //    could be used in e.g. tests, and in other cases there we could 100% guarantee no usages anymore,
  //    and we're ready to take risk of JVM crash if we're wrong.


  //Keep track of mapped buffers allocated & their total size, numbers are reported to OTel.Metrics.
  //Why: mapped buffers are limited resources (~4096 per app by default), so it is worth to monitor
  //     how we use them, and issue the alarm early on as we start to use too many
  private static final AtomicInteger storages = new AtomicInteger();
  private static final AtomicInteger totalPagesMapped = new AtomicInteger();
  private static final AtomicLong totalBytesMapped = new AtomicLong();
  /** total time (nanos) spent inside {@link Page#map(FileChannel, int)} call */
  private static final AtomicLong totalTimeForPageMapNs = new AtomicLong();

  /** Log warn if > PAGES_TO_WARN_THRESHOLD pages were mapped */
  private static final int PAGES_TO_WARN_THRESHOLD = getIntProperty("vfs.memory-mapped-storage.pages-to-warn-threshold", 256);

  private final Path storagePath;

  private final int pageSize;
  private final int pageSizeMask;
  private final int pageSizeBits;

  private final FileChannel channel;

  private final transient Object pagesLock = new Object();
  /** see comments in {@link #pageByIndex(int)} */
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private Page[] pages;

  private final long maxFileSize;

  public MMappedFileStorage(Path path,
                            int pageSize) throws IOException {
    this(path, pageSize, 0);
  }

  private MMappedFileStorage(Path path,
                             int pageSize,
                             int pagesCountToMapInitially) throws IOException {
    if (pageSize <= 0) {
      throw new IllegalArgumentException("pageSize(=" + pageSize + ") must be >0");
    }
    if (Integer.bitCount(pageSize) != 1) {
      throw new IllegalArgumentException("pageSize(=" + pageSize + ") must be a power of 2");
    }
    if (pagesCountToMapInitially < 0) {
      throw new IllegalArgumentException("pagesCountToMapInitially(=" + pagesCountToMapInitially + ") must be >= 0");
    }


    pageSizeBits = Integer.numberOfTrailingZeros(pageSize);
    pageSizeMask = pageSize - 1;
    this.pageSize = pageSize;
    this.maxFileSize = pagesCountToMapInitially;

    this.storagePath = path;

    channel = FileChannel.open(storagePath, READ, WRITE, CREATE);

    //map initial pages:
    pages = new Page[pagesCountToMapInitially];
    for (int i = 0; i < pagesCountToMapInitially; i++) {
      pageByIndex(i);
    }

    storages.incrementAndGet();
  }


  public @NotNull Page pageByOffset(long offsetInFile) throws IOException {
    int pageIndex = pageIndexByOffset(offsetInFile);
    return pageByIndex(pageIndex);
  }

  public @NotNull Page pageByIndex(int pageIndex) throws IOException {
    //We access .pages through data-race. This is a benign race, though: basically, the only values one could
    // read from .pages[i] is {null, Page(i)} -- because those are the only values that are written to .pages[i]
    // across all the codebase, and JMM guarantees no 'out-of-thin-air' values even in the presence of data race.
    //Now Page class is immutable (all fields final), hence it could be 'safe-published' even through data race,
    // so if we read non-null value from .pages[pageIndex] value -- it is a correctly initialized Page(pageIndex)
    // value that is OK to use. If we read null -- we dive into .pageByIndexLocked() there everything (.pages
    // resizing and new page mapping) happens under good-old exclusive lock, that guarantees visibility of all
    // changes done by other threads, and absence of Page duplicates.
    //
    Page page = pageOrNull(pages, pageIndex);
    if (page == null) {
      page = pageByIndexLocked(pageIndex);
    }
    return page;
  }

  private Page pageByIndexLocked(int pageIndex) throws IOException {
    synchronized (pagesLock) {
      if (!channel.isOpen()) {
        throw new ClosedStorageException("Storage already closed");
      }
      if (pageIndex >= pages.length) {
        pages = Arrays.copyOf(pages, pageIndex + 1);
      }
      Page page = pages[pageIndex];

      if (page == null) {
        page = new Page(pageIndex, channel, pageSize, byteOrder());
        pages[pageIndex] = page;

        registerMappedPage(pageSize);
      }
      return page;
    }
  }


  private static @Nullable Page pageOrNull(Page[] pages,
                                           int pageIndex) {
    if (0 <= pageIndex && pageIndex < pages.length) {
      return pages[pageIndex];
    }
    return null;
  }

  public int pageIndexByOffset(long offsetInFile) {
    if (offsetInFile < 0) {
      throw new IllegalArgumentException("offsetInFile(=" + offsetInFile + ") must be >=0");
    }
    return (int)(offsetInFile >> pageSizeBits);
  }

  public int toOffsetInPage(long offsetInFile) {
    return (int)(offsetInFile & pageSizeMask);
  }

  @Override
  public void close() throws IOException {
    synchronized (pagesLock) {
      if (channel.isOpen()) {
        channel.close();
        for (Page page : pages) {
          if (page != null) {
            unregisterMappedPage(pageSize);
          }
        }
        //actual buffer unmap()-ing is done later, by GC
        // let's not delay it by keeping references:
        Arrays.fill(pages, null);

        storages.decrementAndGet();
      }
    }
  }


  public void fsync() throws IOException {
    if (channel.isOpen()) {
      channel.force(true);
    }
  }

  public Path storagePath() {
    return storagePath;
  }

  public int pageSize() {
    return pageSize;
  }

  public boolean isOpen() {
    return channel.isOpen();
  }

  /**
   * Fills with zeroes a region {@code [startOffsetInFile..endOffsetInFile]} (both ends inclusive)
   * </p>
   * <b>BEWARE</b>: Method checks offsets for negativity, and throws {@link IllegalArgumentException}, but doesn't
   * check anything else, just does what was asked: i.e.
   * 1) if (end < start)         => method zeroize nothing
   * 2) if (end   > end-of-file) => method zeroize until the end requested, _expanding_ file along the way
   */
  public void zeroizeRegion(long startOffsetInFile,
                            long endOffsetInFile) throws IOException {
    if (startOffsetInFile < 0) {
      throw new IllegalArgumentException("startOffsetInFile(=" + startOffsetInFile + ") must be >=0");
    }
    if (endOffsetInFile < 0) {
      throw new IllegalArgumentException("endOffsetInFile(=" + endOffsetInFile + ") must be >=0");
    }
    for (long offset = startOffsetInFile; offset <= endOffsetInFile; ) {
      Page page = pageByOffset(offset);
      ByteBuffer pageBuffer = page.rawPageBuffer();

      int startOffsetInPage = toOffsetInPage(offset);
      int endOffsetInPage = endOffsetInFile > page.lastOffsetInFile() ?
                            pageSize - 1 : toOffsetInPage(endOffsetInFile);
      //MAYBE RC: it could be done much faster -- with putLong(), with preallocated array of zeroes,
      //          with Unsafe.setMemory() -- but does it worth it?
      for (int pos = startOffsetInPage; pos <= endOffsetInPage; pos++) {
        //TODO RC: make putLong() (but check both startOffsetInPage and endOffsetInPage are 64-aligned)
        pageBuffer.put(pos, (byte)0);
      }

      offset += (endOffsetInPage - startOffsetInPage) + 1;
    }
  }

  public void zeroizeTillEOF(long startOffsetInFile) throws IOException {
    long actualFileSize = actualFileSize();
    if (actualFileSize == 0) {
      return;
    }
    zeroizeRegion(startOffsetInFile, actualFileSize - 1);
  }

  /**
   * @return current file size. Returned size is always N*pageSize
   * </p>
   * <b>BEWARE</b>: file pages are mapped <i>lazily</i>, hence if file size = N*pageSize -- it does NOT mean there
   * are N {@link Page}s currently mapped into memory -- it could be only pages #0, 5, 8, N-1 are mapped, but
   * pages in between those were never requested, hence not mapped (yet?).
   */
  public long actualFileSize() throws IOException {
    synchronized (pagesLock) {
      //RC: Lock the pages to prevent file expansion (file expansion also acquires .pagesLock, see .pageByOffset())
      //    If file is expanded concurrently with this method, we could see not-pageSize-aligned file size,
      //    which is confusing to deal with.
      //    Better to just prohibit such cases: file expansion (=new page allocation) is a relatively rare
      //    event, so this lock is mostly uncontended, so the cost is negligible.
      long channelSize = channel.size();
      if ((channelSize & pageSizeMask) != 0) {
        throw new AssertionError("Bug: channelSize(=" + channelSize + ") is not pageSize(=" + pageSize + ")-aligned");
      }
      return channelSize;
    }
  }

  public ByteOrder byteOrder() {
    return nativeOrder();
  }

  @Override
  public String toString() {
    return "MMappedFileStorage[" + storagePath + "]" +
           "[pageSize: " + pageSize +
           ", maxFileSize: " + maxFileSize +
           ", pages: " + pages.length +
           ']';
  }

  public static final class Page {
    private final int pageIndex;
    private final int pageSize;
    private final long offsetInFile;
    private final ByteBuffer pageBuffer;

    private Page(int pageIndex,
                 @NotNull FileChannel channel,
                 int pageSize,
                 @NotNull ByteOrder byteOrder) throws IOException {
      this.pageIndex = pageIndex;
      this.pageSize = pageSize;
      this.offsetInFile = pageIndex * (long)pageSize;
      this.pageBuffer = map(channel, pageSize);
      pageBuffer.order(byteOrder);
    }

    private MappedByteBuffer map(FileChannel channel,
                                 int pageSize) throws IOException {
      //MAYBE RC: this could cause noticeable pauses, hence it may worth to enlarge file in advance, async?
      //          i.e. schedule enlargement as soon as last page is 50% full? It wouldn't work good for
      //          completely random-access storages, but most our use-cases are either append-only logs,
      //          or file-attributes, which indexed by fileId, which are growing quite monotonically,
      //          so it may work
      long startedAtNs = System.nanoTime();
      try {
        IOUtil.allocateFileRegion(channel, offsetInFile + pageSize);
        return channel.map(READ_WRITE, offsetInFile, pageSize);
      }
      finally {
        long timeSpentNs = System.nanoTime() - startedAtNs;
        totalTimeForPageMapNs.addAndGet(timeSpentNs);
      }
    }

    public ByteBuffer rawPageBuffer() {
      return pageBuffer;
    }

    public long firstOffsetInFile() {
      return offsetInFile;
    }

    public long lastOffsetInFile() {
      return offsetInFile + pageSize - 1;
    }

    @Override
    public String toString() {
      return "Page[#" + pageIndex + "][offset: " + offsetInFile + ", length: " + pageBuffer.capacity() + " b)";
    }
  }

  // ============ statistics accessors ======================================================================

  public static int storages() {
    return storages.get();
  }

  public static int totalPagesMapped() {
    return totalPagesMapped.get();
  }

  public static long totalBytesMapped() {
    return totalBytesMapped.get();
  }

  /** total time spent inside {@link Page#map(FileChannel, int)} call (including file expansion/zeroing, if needed) */
  public static long totalTimeForPageMap(@NotNull TimeUnit unit) {
    return unit.convert(totalTimeForPageMapNs.get(), NANOSECONDS);
  }

  // ============ statistics infra  ========================================================================

  private static void registerMappedPage(int pageSize) {
    int pagesMapped = totalPagesMapped.incrementAndGet();
    totalBytesMapped.addAndGet(pageSize);

    if (pagesMapped > PAGES_TO_WARN_THRESHOLD) {
      THROTTLED_LOG.warn("Too many pages were mapped: " + pagesMapped + " > " + PAGES_TO_WARN_THRESHOLD + " threshold. " +
                         "Total mapped size: " + totalBytesMapped.get() + " bytes, storages: " + storages.get());
    }
  }

  private static void unregisterMappedPage(int pageSize) {
    totalPagesMapped.decrementAndGet();
    totalBytesMapped.addAndGet(-pageSize);
  }
}
