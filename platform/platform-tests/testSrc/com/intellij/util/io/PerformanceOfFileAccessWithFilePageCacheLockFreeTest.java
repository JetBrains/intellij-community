// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.io.pagecache.Page;
import com.intellij.util.io.pagecache.PageUnsafe;
import com.intellij.util.io.pagecache.impl.PageContentLockingStrategy;
import org.HdrHistogram.Histogram;
import org.jetbrains.annotations.NotNull;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static org.junit.Assume.assumeTrue;

/**
 * Measures throughput:
 * 1. read/write
 * 2. sequential/random
 * 3. via FilePageCacheLockFree
 * 4. single/multi-threaded (THREADS)
 * <p>
 * How to run:
 * 1. Better run in 'forking' mode, so each benchmark uses a dedicated JVM
 * (you'll need to remove @Ignore then)
 * 2. Better use FILE_SIZE >= RAM, so OS page cache is not able to fulfill all the requests.
 * But keep in mind: benchmark uses (FILE_SIZE * THREADS) of disk space.
 */
@FixMethodOrder(MethodSorters.JVM)
public class PerformanceOfFileAccessWithFilePageCacheLockFreeTest extends PerformanceOfFileAccessBaseTest {


  /** Write/read each file more than once -- to be sure :) */
  private static final int TWICE = 2;

  private final StorageLockContext storageContext = new StorageLockContext(true, true, true);

  @BeforeClass
  public static void checkLockFreePageCacheIsEnabled() throws Exception {
    assumeTrue("PageCacheUtils.LOCK_FREE_PAGE_CACHE_ENABLED must be set for this test to run",
               PageCacheUtils.LOCK_FREE_PAGE_CACHE_ENABLED);
  }

  //======================= Single-threaded, with FilePageCache NEW ========

  @Test
  public void singleThreaded_Sequential_Read_ViaFilePageCacheNew() throws IOException, InterruptedException {
    final File file = createRandomContentFileOfSize(FILE_SIZE);
    final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

    final long startedAtNs = System.nanoTime();

    readFileSequentiallyTwice(file, buffer);

    final long finishedAtNs = System.nanoTime();
    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalReadBytes = TWICE * FILE_SIZE;

    printReportForThroughput("Read sequentially, via PageCacheNew", totalReadBytes, elapsedNs);

    System.out.println(storageContext.pageCache().getStatistics().toPrettyString());
  }

  @Test
  public void singleThreaded_Random_Read_ViaFilePageCacheNew() throws IOException, InterruptedException {
    final File file = createRandomContentFileOfSize(FILE_SIZE);
    final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

    final long startedAtNs = System.nanoTime();

    readFileRandomlyApproximatelyTwice(file, buffer);

    final long finishedAtNs = System.nanoTime();

    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalReadBytes = TWICE * FILE_SIZE;

    printReportForThroughput("Read randomly, via PageCacheNew", totalReadBytes, elapsedNs);

    System.out.println(storageContext.pageCache().getStatistics().toPrettyString());
  }

  @Test
  public void singleThreaded_Sequential_Write_ViaFilePageCacheNew() throws IOException, InterruptedException {
    final File file = createRandomContentFileOfSize(FILE_SIZE);
    final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

    final long startedAtNs = System.nanoTime();

    writeFileSequentiallyTwice(file, buffer);

    final long finishedAtNs = System.nanoTime();

    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalReadBytes = TWICE * FILE_SIZE;
    printReportForThroughput("Write sequentially, via PageCacheNew", totalReadBytes, elapsedNs);
  }

  @Test
  public void singleThreaded_Random_Write_ViaFilePageCacheNew() throws IOException, InterruptedException {
    final File file = createRandomContentFileOfSize(FILE_SIZE);
    final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

    final long startedAtNs = System.nanoTime();

    writeFileRandomlyApproximatelyTwice(file, buffer);

    final long finishedAtNs = System.nanoTime();
    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalReadBytes = TWICE * FILE_SIZE;

    printReportForThroughput("Write randomly, via PageCacheNew", totalReadBytes, elapsedNs);

    System.out.println(storageContext.pageCache().getStatistics().toPrettyString());
  }

  //======================= Multi-threaded, with FilePageCache ========

  @Test
  public void multiThreaded_Uncontended_Sequential_Read_ViaPageCacheNew() throws IOException, InterruptedException {
    final Runnable[] tasks = new Runnable[THREADS];
    for (int taskNo = 0; taskNo < tasks.length; taskNo++) {
      final File file = createRandomContentFileOfSize(FILE_SIZE);
      final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

      tasks[taskNo] = () -> {
        try {
          readFileSequentiallyTwice(file, buffer);
        }
        catch (Exception e) {
          throw new AssertionError(e);
        }
      };
    }

    final long startedAtNs = System.nanoTime();

    runThroughputTasksMultiThreaded(tasks);

    final long finishedAtNs = System.nanoTime();

    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalReadBytes = TWICE * FILE_SIZE * THREADS;
    printReportForThroughput("Read sequentially, " + THREADS + " threads, uncontended, via PageCacheNew", totalReadBytes, elapsedNs);
  }

  @Test
  public void multiThreaded_Uncontended_Random_Read_ViaPageCacheNew() throws IOException, InterruptedException {
    final Runnable[] tasks = new Runnable[THREADS];
    for (int taskNo = 0; taskNo < tasks.length; taskNo++) {
      final File file = createRandomContentFileOfSize(FILE_SIZE);
      final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

      tasks[taskNo] = () -> {
        try {
          readFileRandomlyApproximatelyTwice(file, buffer);
        }
        catch (Exception e) {
          throw new AssertionError(e);
        }
      };
    }

    final long startedAtNs = System.nanoTime();

    runThroughputTasksMultiThreaded(tasks);

    final long finishedAtNs = System.nanoTime();

    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalReadBytes = TWICE * FILE_SIZE * THREADS;
    printReportForThroughput("Read randomly, " + THREADS + " threads, uncontended, via PageCacheNew", totalReadBytes, elapsedNs);
  }

  @Test
  public void multiThreaded_Uncontended_Sequential_Write_ViaPageCacheNew() throws IOException, InterruptedException {
    final Runnable[] tasks = new Runnable[THREADS];
    for (int taskNo = 0; taskNo < tasks.length; taskNo++) {
      final File file = createRandomContentFileOfSize(FILE_SIZE);
      final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);
      tasks[taskNo] = () -> {
        try {
          writeFileSequentiallyTwice(file, buffer);
        }
        catch (Exception e) {
          throw new AssertionError(e);
        }
      };
    }

    final long startedAtNs = System.nanoTime();

    runThroughputTasksMultiThreaded(tasks);

    final long finishedAtNs = System.nanoTime();

    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalWrittenBytes = TWICE * FILE_SIZE * THREADS;
    printReportForThroughput("Written sequentially, " + THREADS + " threads, uncontended, via PageCacheNew", totalWrittenBytes, elapsedNs);
  }

  @Test
  public void multiThreaded_Uncontended_Random_Write_ViaPageCacheNew() throws IOException, InterruptedException {
    final Runnable[] tasks = new Runnable[THREADS];
    for (int taskNo = 0; taskNo < tasks.length; taskNo++) {
      final File file = createRandomContentFileOfSize(FILE_SIZE);
      final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

      tasks[taskNo] = () -> {
        try {
          writeFileRandomlyApproximatelyTwice(file, buffer);
        }
        catch (Exception e) {
          throw new AssertionError(e);
        }
      };
    }

    final long startedAtNs = System.nanoTime();

    runThroughputTasksMultiThreaded(tasks);

    final long finishedAtNs = System.nanoTime();

    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalWrittenBytes = TWICE * FILE_SIZE * THREADS;
    printReportForThroughput("Written randomly, " + THREADS + " threads, uncontended, via PageCacheNew", totalWrittenBytes, elapsedNs);
  }

  //======================= Response-time (one-shot): ========

  @Test
  public void responseTime_Random_Read_ViaFilePageCacheNew() throws IOException, InterruptedException {
    final File file = createRandomContentFileOfSize(FILE_SIZE);
    final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);
    final FilePageCacheLockFree pageCache = storageContext.pageCache();

    final long pagesCount = FILE_SIZE / BUFFER_SIZE;
    final long[] offsetsToRequest = IntStream.range(0, DIFFERENT_OFFSETS_TO_REQUEST)
      .mapToLong(i -> ThreadLocalRandom.current().nextLong(FILE_SIZE))
      .toArray();
    try (final PagedFileStorageWithRWLockedPageContent pagedStorage = new PagedFileStorageWithRWLockedPageContent(file.toPath(),
                                                                                                                  storageContext,
                                                                                                                  BUFFER_SIZE,
                                                                                                                  true,
                                                                                                                  PageContentLockingStrategy.LOCK_PER_PAGE)) {
      final Callable<Void> task = () -> {
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();
        final int requestNo = rnd.nextInt(offsetsToRequest.length);
        final long blockOffset = offsetsToRequest[requestNo] % FILE_SIZE;
        try (final PageUnsafe page = (PageUnsafe)pagedStorage.pageByOffset(blockOffset, /*forWrite: */ false)) {
          buffer.clear();

          //emulate 'read':

          //page.read(0, SEGMENT_LENGTH_FOR_RESPONSE_TIME_SHOT,
          //          pageBuffer -> buffer.put(0, pageBuffer, 0, SEGMENT_LENGTH_FOR_RESPONSE_TIME_SHOT));

          page.lockPageForRead();
          try {
            //final ByteBuffer pageBuffer = page.rawPageBuffer().duplicate();
            //buffer.put(0, pageBuffer, 0, SEGMENT_LENGTH_FOR_RESPONSE_TIME_SHOT);

            buffer.put(0, page.rawPageBuffer(), 0, SEGMENT_LENGTH_FOR_RESPONSE_TIME_SHOT);
          }
          finally {
            page.unlockPageForRead();
          }
          return null;
        }
      };

      final long startedAtNs = System.nanoTime();
      final Histogram responseTimeUsHisto = measureTaskResponseTimeMultiThreaded(
        task,
        /* delayBetweenShotsNs: */ DELAY_BETWEEN_RESPONSE_TIME_SHOTS_NS,
        THREADS,
        RESPONSE_TIME_SHOTS
      );
      final long finishedAtNs = System.nanoTime();

      printReportForResponseTime(
        "Read randomly",
        SEGMENT_LENGTH_FOR_RESPONSE_TIME_SHOT,
        DELAY_BETWEEN_RESPONSE_TIME_SHOTS_NS,
        RESPONSE_TIME_SHOTS,
        responseTimeUsHisto
      );

      final long cacheCapacityBytes = pageCache.getCacheCapacityBytes();
      final long expectedPagesAllocated = estimatePagesToLoad(
        cacheCapacityBytes / BUFFER_SIZE,
        DIFFERENT_OFFSETS_TO_REQUEST,
        THREADS * RESPONSE_TIME_SHOTS
      );
      System.out.printf("Cache capacity %d Mb, expected pages to load: %d\n",
                        cacheCapacityBytes / IOUtil.MiB,
                        expectedPagesAllocated
      );
    }
    System.out.println(pageCache.getStatistics().toPrettyString());
  }

  @Test
  public void responseTime_Random_Write_ViaFilePageCacheNew() throws IOException, InterruptedException {
    final File file = createRandomContentFileOfSize(FILE_SIZE);
    final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);
    final FilePageCacheLockFree pageCache = storageContext.pageCache();

    final long pagesCount = FILE_SIZE / BUFFER_SIZE;
    final long[] offsetsToRequest = IntStream.range(0, DIFFERENT_OFFSETS_TO_REQUEST)
      .mapToLong(i -> ThreadLocalRandom.current().nextLong(FILE_SIZE))
      .toArray();
    try (final PagedFileStorageWithRWLockedPageContent pagedStorage = new PagedFileStorageWithRWLockedPageContent(file.toPath(),
                                                                                                                  storageContext,
                                                                                                                  BUFFER_SIZE,
                                                                                                                  true,
                                                                                                                  PageContentLockingStrategy.LOCK_PER_PAGE)) {
      final Callable<Void> task = () -> {
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();
        final int requestNo = rnd.nextInt(offsetsToRequest.length);
        final long blockOffset = offsetsToRequest[requestNo] % FILE_SIZE;
        try (final PageUnsafe page = (PageUnsafe)pagedStorage.pageByOffset(blockOffset, /*forWrite: */ true)) {
          //emulate 'write':
          buffer.clear();
          //page.write(0, SEGMENT_LENGTH_FOR_RESPONSE_TIME_SHOT,
          //           pageBuffer -> pageBuffer.put(0, buffer, 0, SEGMENT_LENGTH_FOR_RESPONSE_TIME_SHOT));
          page.lockPageForWrite();
          try {
            page.rawPageBuffer().put(0, buffer, 0, SEGMENT_LENGTH_FOR_RESPONSE_TIME_SHOT);
            page.regionModified(0, SEGMENT_LENGTH_FOR_RESPONSE_TIME_SHOT);
          }
          finally {
            page.unlockPageForWrite();
          }
          return null;
        }
      };

      final long startedAtNs = System.nanoTime();
      final Histogram responseTimeUsHisto = measureTaskResponseTimeMultiThreaded(
        task,
        DELAY_BETWEEN_RESPONSE_TIME_SHOTS_NS,
        THREADS,
        RESPONSE_TIME_SHOTS
      );
      final long finishedAtNs = System.nanoTime();

      printReportForResponseTime(
        "Write randomly, at " + DIFFERENT_OFFSETS_TO_REQUEST + " offsets",
        SEGMENT_LENGTH_FOR_RESPONSE_TIME_SHOT,
        DELAY_BETWEEN_RESPONSE_TIME_SHOTS_NS,
        RESPONSE_TIME_SHOTS,
        responseTimeUsHisto
      );


      final long cacheCapacityBytes = pageCache.getCacheCapacityBytes();
      final long expectedPagesAllocated = estimatePagesToLoad(
        cacheCapacityBytes / BUFFER_SIZE,
        DIFFERENT_OFFSETS_TO_REQUEST,
        THREADS * RESPONSE_TIME_SHOTS
      );
      System.out.printf("Cache capacity %d Mb, expected pages to load: %d\n",
                        cacheCapacityBytes / IOUtil.MiB,
                        expectedPagesAllocated
      );
    }
    System.out.println(pageCache.getStatistics().toPrettyString());
  }


  // ================= infrastructure: =====================================================================


  private void readFileRandomlyApproximatelyTwice(final @NotNull File file,
                                                  final @NotNull ByteBuffer buffer) throws IOException, InterruptedException {
    try (PagedFileStorageWithRWLockedPageContent pagedStorage = new PagedFileStorageWithRWLockedPageContent(file.toPath(),
                                                                                                            storageContext,
                                                                                                            BUFFER_SIZE,
                                                                                                            true,
                                                                                                            PageContentLockingStrategy.LOCK_PER_PAGE)) {
      final ThreadLocalRandom rnd = ThreadLocalRandom.current();
      final long blocksCount = FILE_SIZE / BUFFER_SIZE;
      for (int i = 0; i < blocksCount * TWICE; i++) {
        final long blockNo = rnd.nextLong(blocksCount);
        final long blockOffset = blockNo * BUFFER_SIZE;
        try (Page page = pagedStorage.pageByOffset(blockOffset, /*forWrite: */ false)) {
          // emulate 'read':
          buffer.clear();
          page.read(0, page.pageSize(),
                    pageBuffer -> buffer.put(pageBuffer));
        }
      }
    }
  }

  private void readFileSequentiallyTwice(final @NotNull File file,
                                         final @NotNull ByteBuffer buffer) throws IOException, InterruptedException {
    try (PagedFileStorageWithRWLockedPageContent pagedStorage = new PagedFileStorageWithRWLockedPageContent(file.toPath(),
                                                                                                            storageContext,
                                                                                                            BUFFER_SIZE,
                                                                                                            true,
                                                                                                            PageContentLockingStrategy.LOCK_PER_PAGE)) {
      final long blocksCount = FILE_SIZE / BUFFER_SIZE;
      for (int turn = 0; turn < TWICE; turn++) {
        for (int blockNo = 0; blockNo < blocksCount; blockNo++) {
          final long blockOffset = blockNo * (long)BUFFER_SIZE;
          try (Page page = pagedStorage.pageByOffset(blockOffset, /*forWrite: */ false)) {
            //emulate 'read':
            buffer.clear();
            page.read(0, page.pageSize(),
                      pageBuffer -> buffer.put(pageBuffer));
          }
        }
      }
    }
  }

  private void writeFileRandomlyApproximatelyTwice(final @NotNull File file,
                                                   final @NotNull ByteBuffer buffer) throws IOException, InterruptedException {

    try (PagedFileStorageWithRWLockedPageContent pagedStorage = new PagedFileStorageWithRWLockedPageContent(file.toPath(),
                                                                                                            storageContext,
                                                                                                            BUFFER_SIZE,
                                                                                                            true,
                                                                                                            PageContentLockingStrategy.LOCK_PER_PAGE)) {
      final ThreadLocalRandom rnd = ThreadLocalRandom.current();
      final long blocksCount = FILE_SIZE / BUFFER_SIZE;
      for (int i = 0; i < blocksCount * TWICE; i++) {
        final long blockNo = rnd.nextLong(blocksCount);
        final long blockOffset = blockNo * BUFFER_SIZE;
        try (Page page = pagedStorage.pageByOffset(blockOffset, /*forWrite: */ true)) {
          // emulate 'write':
          buffer.clear();
          page.putFromBuffer(buffer, 0);
          //page.write(0, page.pageSize(),
          //           pageBuffer -> pageBuffer.put(buffer));
        }
      }
    }
  }

  private void writeFileSequentiallyTwice(final @NotNull File file,
                                          final @NotNull ByteBuffer buffer) throws IOException, InterruptedException {
    try (PagedFileStorageWithRWLockedPageContent pagedStorage = new PagedFileStorageWithRWLockedPageContent(file.toPath(),
                                                                                                            storageContext, BUFFER_SIZE,
                                                                                                            true,
                                                                                                            PageContentLockingStrategy.LOCK_PER_PAGE)) {
      long blocksCount = FILE_SIZE / BUFFER_SIZE;
      for (int turn = 0; turn < TWICE; turn++) {
        for (int blockNo = 0; blockNo < blocksCount; blockNo++) {
          long blockOffset = blockNo * (long)BUFFER_SIZE;
          try (Page page = pagedStorage.pageByOffset(blockOffset, /*forWrite: */ true)) {
            //emulate 'write':
            buffer.clear();
            page.putFromBuffer(buffer, 0);
            //page.write(0, page.pageSize(),
            //           pageBuffer -> pageBuffer.put(buffer));
          }
        }
      }
    }
  }
}