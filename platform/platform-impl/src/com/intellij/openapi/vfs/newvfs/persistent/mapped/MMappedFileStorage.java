// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.mapped;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSRecordsLockFreeOverMMappedFile;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.util.io.ClosedStorageException;
import com.intellij.util.io.IOUtil;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static com.intellij.platform.diagnostic.telemetry.PlatformScopesKt.VFS;
import static com.intellij.util.SystemProperties.getIntProperty;
import static java.nio.ByteOrder.nativeOrder;
import static java.nio.channels.FileChannel.MapMode.READ_WRITE;
import static java.nio.file.StandardOpenOption.*;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Storage over memory-mapped file.
 * Hides most of the peculiarities of mmapped-files.
 * But still very low-level, so use with caution -- or better don't. Better use higher-level components, like
 * {@link MappedFileStorageHelper} or {@link com.intellij.openapi.vfs.newvfs.persistent.dev.FastFileAttributes}
 */
@ApiStatus.Internal
public final class MMappedFileStorage implements Closeable {
  private static final Logger LOG = Logger.getInstance(MMappedFileStorage.class);

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

  static {
    Meter meter = TelemetryManager.getInstance().getMeter(VFS);
    ObservableLongMeasurement storagesCounter = meter.gaugeBuilder("MappedFileStorage.storages")
      .setDescription("MappedFileStorage instances in operation at the moment")
      .ofLongs()
      .buildObserver();
    ObservableLongMeasurement pagesCounter = meter.gaugeBuilder("MappedFileStorage.totalPagesMapped")
      .setDescription("MappedFileStorage.Page instances in operation at the moment")
      .ofLongs()
      .buildObserver();
    ObservableLongMeasurement pagesBytesCounter = meter.gaugeBuilder("MappedFileStorage.totalBytesMapped")
      .setDescription("Total size of MappedByteBuffers in use by MappedFileStorage at the moment")
      .setUnit("bytes")
      .ofLongs()
      .buildObserver();
    ObservableLongMeasurement mappingTimeCounter = meter.gaugeBuilder("MappedFileStorage.totalTimeSpentOnMappingUs")
      .setDescription("Total time (us) spent inside Page.map() method (file enlargement/zeroing + map)")
      .setUnit("us")
      .ofLongs()
      .buildObserver();
    meter.batchCallback(
      () -> {
        storagesCounter.record(storages.get());
        pagesCounter.record(totalPagesMapped.get());
        pagesBytesCounter.record(totalBytesMapped.get());
        mappingTimeCounter.record(NANOSECONDS.toMicros(totalTimeForPageMapNs.get()));
      },
      storagesCounter, pagesCounter, pagesBytesCounter, mappingTimeCounter
    );
  }

  private final Path storagePath;

  private final int pageSize;
  private final int pageSizeMask;
  private final int pageSizeBits;

  private final FileChannel channel;

  private final AtomicReferenceArray<Page> pages;

  private final long maxFileSize;

  public MMappedFileStorage(final Path path,
                            final int pageSize) throws IOException {
    //TODO RC: maybe instead of setting maxSize -- make .pages re-allocable?
    //         It is very rare to have all 2^32 records in VFS
    this(path, pageSize, PersistentFSRecordsLockFreeOverMMappedFile.RECORD_SIZE_IN_BYTES * (long)Integer.MAX_VALUE);
  }

  public MMappedFileStorage(final Path path,
                            final int pageSize,
                            final long maxFileSize) throws IOException {
    if (pageSize <= 0) {
      throw new IllegalArgumentException("pageSize(=" + pageSize + ") must be >0");
    }
    if (maxFileSize <= 0) {
      throw new IllegalArgumentException("maxFileSize(=" + maxFileSize + ") must be >0");
    }
    if (Integer.bitCount(pageSize) != 1) {
      throw new IllegalArgumentException("pageSize(=" + pageSize + ") must be a power of 2");
    }


    pageSizeBits = Integer.numberOfTrailingZeros(pageSize);
    pageSizeMask = pageSize - 1;
    this.pageSize = pageSize;
    this.maxFileSize = maxFileSize;

    this.storagePath = path;

    final int maxPagesCount = Math.toIntExact(maxFileSize / pageSize);

    final long length = Files.exists(path) ? Files.size(path) : 0;

    final int pagesToMapExistingFileContent = (int)((length % pageSize == 0) ?
                                                    (length / pageSize) :
                                                    ((length / pageSize) + 1));
    if (pagesToMapExistingFileContent > maxPagesCount) {
      throw new IllegalStateException(
        "Storage size(" + length + " b) > maxFileSize(" + maxFileSize + " b): " +
        "file [" + path + "] is corrupted?");
    }

    //allocate array(1200+): not so much to worry about
    pages = new AtomicReferenceArray<>(maxPagesCount);

    channel = FileChannel.open(storagePath, READ, WRITE, CREATE);

    //map already existing file content right away:
    for (int i = 0; i < pagesToMapExistingFileContent; i++) {
      Page page = new Page(i, channel, pageSize);
      pages.set(i, page);

      totalPagesMapped.incrementAndGet();
      totalBytesMapped.addAndGet(pageSize);

      long pagesMapped = totalPagesMapped.get();
      if (pagesMapped > PAGES_TO_WARN_THRESHOLD) {
        LOG.warn(pagesMapped + " pages were mapped -- too many, > " + PAGES_TO_WARN_THRESHOLD + " threshold. " +
                 "Total mapped size: " + totalBytesMapped.get() + " bytes, storages: " + storages.get());
      }
    }

    storages.incrementAndGet();
  }


  public @NotNull Page pageByOffset(final long offsetInFile) throws IOException {
    final int pageIndex = pageIndexByOffset(offsetInFile);

    Page page = pages.get(pageIndex);
    if (page == null) {
      synchronized (pages) {
        if (!channel.isOpen()) {
          throw new ClosedStorageException("Storage already closed");
        }
        page = pages.get(pageIndex);
        if (page == null) {
          page = new Page(pageIndex, channel, pageSize);
          pages.set(pageIndex, page);

          totalPagesMapped.incrementAndGet();
          totalBytesMapped.addAndGet(pageSize);
        }
      }
    }
    return page;
  }

  public int pageIndexByOffset(final long offsetInFile) {
    if (offsetInFile < 0) {
      throw new IllegalArgumentException("offsetInFile(=" + offsetInFile + ") must be >=0");
    }
    return (int)(offsetInFile >> pageSizeBits);
  }

  public int toOffsetInPage(final long offsetInFile) {
    return (int)(offsetInFile & pageSizeMask);
  }

  @Override
  public void close() throws IOException {
    synchronized (pages) {
      if (channel.isOpen()) {
        channel.close();
        for (int i = 0; i < pages.length(); i++) {
          final Page page = pages.get(i);
          if (page != null) {
            page.close();
            //actual buffer releasing happens later, by GC, but at least we don't keep it
            totalPagesMapped.decrementAndGet();
            totalBytesMapped.addAndGet(-pageSize);
          }
          pages.set(i, null);//give GC a chance to unmap buffers
        }
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

  @Override
  public String toString() {
    return "MMappedFileStorage[" + storagePath + "]" +
           "[pageSize: " + pageSize +
           ", maxFileSize: " + maxFileSize +
           ", pages: " + pages.length() +
           ']';
  }

  public static final class Page implements AutoCloseable {
    private final int pageIndex;
    private final long offsetInFile;
    private final ByteBuffer pageBuffer;

    private Page(int pageIndex,
                 FileChannel channel,
                 int pageSize) throws IOException {
      this.pageIndex = pageIndex;
      this.offsetInFile = pageIndex * (long)pageSize;
      this.pageBuffer = map(channel, pageSize);
      pageBuffer.order(nativeOrder());
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

    @Override
    public void close() {
      //nothing so far, reserved for future
    }

    public ByteBuffer rawPageBuffer() {
      return pageBuffer;
    }

    @Override
    public String toString() {
      return "Page[#" + pageIndex + "][offset: " + offsetInFile + ", length: " + pageBuffer.capacity() + " b)";
    }
  }
}
