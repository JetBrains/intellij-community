// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.io.stats.FilePageCacheStatistics;
import org.HdrHistogram.Histogram;
import org.jetbrains.annotations.NotNull;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/**
 * Measures throughput:
 * 1. read/write
 * 2. sequential/random
 * 3. via FilePageCache
 * 4. single/multi-threaded (THREADS)
 * <p>
 * How to run:
 * 1. Better run in 'forking' mode, so each benchmark uses a dedicated JVM
 * (you'll need to remove @Ignore then)
 * 2. Better use FILE_SIZE >= RAM, so OS page cache is not able to fulfill all the requests.
 * But keep in mind: benchmark uses (FILE_SIZE * THREADS) of disk space.
 */
@FixMethodOrder(MethodSorters.JVM)
public class PerformanceOfFileAccessWithFilePageCacheTest extends PerformanceOfFileAccessBaseTest {

  /** Write/read each file more than once -- to be sure :) */
  private static final int TWICE = 2;


  private final StorageLockContext storageContext = new StorageLockContext(true, true, true);

  //======================= Single-threaded: ========

  @Test
  public void singleThreaded_Sequential_Read_ViaFilePageCache() throws IOException {
    final File file = createRandomContentFileOfSize(FILE_SIZE);
    final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

    final long startedAtNs = System.nanoTime();

    readFileSequentiallyTwice(file, buffer);

    final long finishedAtNs = System.nanoTime();
    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalReadBytes = TWICE * FILE_SIZE;
    printReportForThroughput("Read sequentially, with PageCache", totalReadBytes, elapsedNs);
  }

  @Test
  public void singleThreaded_Random_Read_ViaFilePageCache() throws IOException {
    final File file = createRandomContentFileOfSize(FILE_SIZE);
    final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

    final long startedAtNs = System.nanoTime();

    readFileRandomlyApproximatelyTwice(file, buffer);

    final long finishedAtNs = System.nanoTime();
    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalReadBytes = TWICE * FILE_SIZE;
    printReportForThroughput("Read randomly, with PageCache", totalReadBytes, elapsedNs);
  }

  @Test
  public void singleThreaded_Sequential_Write_ViaFilePageCache() throws IOException {
    final File file = createRandomContentFileOfSize(FILE_SIZE);
    final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

    final long startedAtNs = System.nanoTime();

    writeFileSequentiallyTwice(file, buffer);

    final long finishedAtNs = System.nanoTime();

    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalReadBytes = TWICE * FILE_SIZE;
    printReportForThroughput("Write sequentially, with PageCache", totalReadBytes, elapsedNs);
  }

  @Test
  public void singleThreaded_Random_Write_ViaFilePageCache() throws IOException {
    final File file = createRandomContentFileOfSize(FILE_SIZE);
    final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

    final long startedAtNs = System.nanoTime();

    writeFileRandomlyApproximatelyTwice(file, buffer);

    final long finishedAtNs = System.nanoTime();
    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalReadBytes = TWICE * FILE_SIZE;
    printReportForThroughput("Write randomly, with PageCache", totalReadBytes, elapsedNs);
  }


  //======================= Multi-threaded: ========

  @Test
  public void multiThreaded_Uncontended_Sequential_Read_ViaPageCache() throws IOException, InterruptedException {
    final Runnable[] tasks = new Runnable[THREADS];
    for (int taskNo = 0; taskNo < tasks.length; taskNo++) {
      final File file = createRandomContentFileOfSize(FILE_SIZE);
      final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

      tasks[taskNo] = () -> {
        try {
          readFileSequentiallyTwice(file, buffer);
        }
        catch (IOException e) {
          throw new AssertionError(e);
        }
      };
    }

    final long startedAtNs = System.nanoTime();

    runThroughputTasksMultiThreaded(tasks);

    final long finishedAtNs = System.nanoTime();

    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalReadBytes = TWICE * FILE_SIZE * THREADS;
    printReportForThroughput("Read sequentially, " + THREADS + " threads, uncontended, via PageCache", totalReadBytes, elapsedNs);
  }

  @Test
  public void multiThreaded_Uncontended_Random_Read_ViaPageCache() throws IOException, InterruptedException {
    final Runnable[] tasks = new Runnable[THREADS];
    for (int taskNo = 0; taskNo < tasks.length; taskNo++) {
      final File file = createRandomContentFileOfSize(FILE_SIZE);
      final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

      tasks[taskNo] = () -> {
        try {
          readFileRandomlyApproximatelyTwice(file, buffer);
        }
        catch (IOException e) {
          throw new AssertionError(e);
        }
      };
    }

    final long startedAtNs = System.nanoTime();

    runThroughputTasksMultiThreaded(tasks);

    final long finishedAtNs = System.nanoTime();

    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalReadBytes = TWICE * FILE_SIZE * THREADS;
    printReportForThroughput("Read randomly, " + THREADS + " threads, uncontended, via PageCache", totalReadBytes, elapsedNs);
  }

  @Test
  public void multiThreaded_Uncontended_Sequential_Write_ViaPageCache() throws IOException, InterruptedException {
    final Runnable[] tasks = new Runnable[THREADS];
    for (int taskNo = 0; taskNo < tasks.length; taskNo++) {
      final File file = createRandomContentFileOfSize(FILE_SIZE);
      final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);
      tasks[taskNo] = () -> {
        try {
          writeFileSequentiallyTwice(file, buffer);
        }
        catch (IOException e) {
          throw new AssertionError(e);
        }
      };
    }

    final long startedAtNs = System.nanoTime();

    runThroughputTasksMultiThreaded(tasks);

    final long finishedAtNs = System.nanoTime();

    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalWrittenBytes = TWICE * FILE_SIZE * THREADS;
    printReportForThroughput("Written sequentially, " + THREADS + " threads, uncontended, via PageCache", totalWrittenBytes, elapsedNs);
  }

  @Test
  public void multiThreaded_Uncontended_Random_Write_ViaPageCache() throws IOException, InterruptedException {
    final Runnable[] tasks = new Runnable[THREADS];
    for (int taskNo = 0; taskNo < tasks.length; taskNo++) {
      final File file = createRandomContentFileOfSize(FILE_SIZE);
      final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

      tasks[taskNo] = () -> {
        try {
          writeFileRandomlyApproximatelyTwice(file, buffer);
        }
        catch (IOException e) {
          throw new AssertionError(e);
        }
      };
    }

    final long startedAtNs = System.nanoTime();

    runThroughputTasksMultiThreaded(tasks);

    final long finishedAtNs = System.nanoTime();

    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalWrittenBytes = TWICE * FILE_SIZE * THREADS;
    printReportForThroughput("Written randomly, " + THREADS + " threads, uncontended, via PageCache", totalWrittenBytes, elapsedNs);
  }

  //======================= Response-time (one-shot): ========

  @Test
  public void responseTime_Random_Read_ViaFilePageCache() throws IOException, InterruptedException {
    final File file = createRandomContentFileOfSize(FILE_SIZE);
    final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

    final long pagesCount = FILE_SIZE / BUFFER_SIZE;
    final long[] offsetsToRequest = IntStream.range(0, DIFFERENT_OFFSETS_TO_REQUEST)
      .mapToLong(i -> ThreadLocalRandom.current().nextLong(FILE_SIZE))
      .toArray();
    try (PagedFileStorage pagedStorage = new PagedFileStorage(file.toPath(), storageContext, BUFFER_SIZE, true, true)) {
      final Callable<Void> task = () -> {
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();
        final int requestNo = rnd.nextInt(offsetsToRequest.length);
        final long blockOffset = offsetsToRequest[requestNo] % FILE_SIZE;
        pagedStorage.lockRead();
        try {
          final DirectBufferWrapper page = pagedStorage.getByteBuffer(blockOffset, /*forWrite: */ false);
          try {
            //emulate 'read':
            buffer.clear();
            buffer.put(0, page.getBuffer(), 0, SEGMENT_LENGTH_FOR_RESPONSE_TIME_SHOT);
            return null;
          }
          finally {
            page.unlock();
          }
        }
        finally {
          pagedStorage.unlockRead();
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
        "Read randomly",
        SEGMENT_LENGTH_FOR_RESPONSE_TIME_SHOT,
        DELAY_BETWEEN_RESPONSE_TIME_SHOTS_NS,
        RESPONSE_TIME_SHOTS,
        responseTimeUsHisto
      );

      final FilePageCache pageCache = storageContext.getBufferCache();
      final long cacheCapacityBytes = pageCache.getMaxSize();
      final long expectedPagesAllocated = estimatePagesToLoad(
        cacheCapacityBytes / BUFFER_SIZE,
        DIFFERENT_OFFSETS_TO_REQUEST,
        THREADS * RESPONSE_TIME_SHOTS
      );
      System.out.printf("Cache capacity %d Mb, expected pages to load: %d\n",
                        cacheCapacityBytes / IOUtil.MiB,
                        expectedPagesAllocated
      );

      final FilePageCacheStatistics statistics = pageCache.getStatistics();
      System.out.println(statistics);
    }
  }

  @Test
  public void responseTime_Random_Write_ViaFilePageCache() throws IOException, InterruptedException {
    final File file = createRandomContentFileOfSize(FILE_SIZE);
    final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

    final long pagesCount = FILE_SIZE / BUFFER_SIZE;
    final long[] offsetsToRequest = IntStream.range(0, DIFFERENT_OFFSETS_TO_REQUEST)
      .mapToLong(i -> ThreadLocalRandom.current().nextLong(FILE_SIZE))
      .toArray();
    try (PagedFileStorage pagedStorage = new PagedFileStorage(file.toPath(), storageContext, BUFFER_SIZE, true, true)) {
      final Callable<Void> task = () -> {
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();
        final int requestNo = rnd.nextInt(offsetsToRequest.length);
        final long blockOffset = offsetsToRequest[requestNo] % FILE_SIZE;
        pagedStorage.lockWrite();
        try {
          final DirectBufferWrapper page = pagedStorage.getByteBuffer(blockOffset, /*forWrite: */ true);
          try {
            //emulate 'write':
            buffer.clear();
            page.putFromBuffer(buffer, 0);
            //page.getBuffer().put(0, buffer, 0, SEGMENT_LENGTH_FOR_RESPONSE_TIME_SHOT);
            //page.markDirty();
            //page.fileSizeMayChanged(SEGMENT_LENGTH_FOR_RESPONSE_TIME_SHOT);
            return null;
          }
          finally {
            page.unlock();
          }
        }
        finally {
          pagedStorage.unlockWrite();
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

      final FilePageCache pageCache = storageContext.getBufferCache();
      final long cacheCapacityBytes = pageCache.getMaxSize();
      final long expectedPagesAllocated = estimatePagesToLoad(
        cacheCapacityBytes / BUFFER_SIZE,
        DIFFERENT_OFFSETS_TO_REQUEST,
        THREADS * RESPONSE_TIME_SHOTS
      );
      System.out.printf("Cache capacity %d Mb, expected pages to load: %d\n",
                        cacheCapacityBytes / IOUtil.MiB,
                        expectedPagesAllocated
      );

      final FilePageCacheStatistics statistics = pageCache.getStatistics();
      System.out.println(statistics);
    }
  }


  // ================= infrastructure: =====================================================================

  private void readFileSequentiallyTwice(final @NotNull File file,
                                         final @NotNull ByteBuffer buffer) throws IOException {
    try (PagedFileStorage pagedStorage = new PagedFileStorage(file.toPath(), storageContext, BUFFER_SIZE, true, true)) {
      final long blocksCount = FILE_SIZE / BUFFER_SIZE;
      for (int turn = 0; turn < TWICE; turn++) {
        for (int blockNo = 0; blockNo < blocksCount; blockNo++) {
          final long blockOffset = blockNo * (long)BUFFER_SIZE;
          pagedStorage.lockRead();
          try {
            final DirectBufferWrapper page = pagedStorage.getByteBuffer(blockOffset, /*forWrite: */ false);
            try {
              //emulate 'read':
              buffer.clear();
              buffer.put(0, page.getBuffer(), 0, BUFFER_SIZE);
              //buffer.put(page.getBuffer().duplicate());
            }
            finally {
              page.unlock();
            }
          }
          finally {
            pagedStorage.unlockRead();
          }
        }
      }
    }
  }

  private void readFileRandomlyApproximatelyTwice(final @NotNull File file,
                                                  final @NotNull ByteBuffer buffer) throws IOException {
    try (PagedFileStorage pagedStorage = new PagedFileStorage(file.toPath(), storageContext, BUFFER_SIZE, true, true)) {
      final ThreadLocalRandom rnd = ThreadLocalRandom.current();
      final long blocksCount = FILE_SIZE / BUFFER_SIZE;
      for (int i = 0; i < blocksCount * TWICE; i++) {
        final long blockNo = rnd.nextLong(blocksCount);//(i * 11579L) % blocksCount;
        final long blockOffset = blockNo * BUFFER_SIZE;
        pagedStorage.lockRead();
        try {
          final DirectBufferWrapper page = pagedStorage.getByteBuffer(blockOffset, /*forWrite: */ false);
          try {
            // emulate 'read':
            buffer.clear();
            buffer.put(0, page.getBuffer(), 0, BUFFER_SIZE);
            //buffer.put(page.getBuffer().duplicate());
          }
          finally {
            page.unlock();
          }
        }
        finally {
          pagedStorage.unlockRead();
        }
      }
    }
  }

  private void writeFileSequentiallyTwice(final @NotNull File file,
                                          final @NotNull ByteBuffer buffer) throws IOException {
    try (PagedFileStorage pagedStorage = new PagedFileStorage(file.toPath(), storageContext, BUFFER_SIZE, true, true)) {
      final long blocksCount = FILE_SIZE / BUFFER_SIZE;
      for (int turn = 0; turn < TWICE; turn++) {
        for (int blockNo = 0; blockNo < blocksCount; blockNo++) {
          final long blockOffset = blockNo * (long)BUFFER_SIZE;
          pagedStorage.lockWrite();
          try {
            final DirectBufferWrapper page = pagedStorage.getByteBuffer(blockOffset, /*forWrite: */ true);
            try {
              //emulate 'write':
              buffer.clear();
              page.putFromBuffer(buffer, 0);
            }
            finally {
              page.unlock();
            }
          }
          finally {
            pagedStorage.unlockWrite();
          }
        }
      }
    }
  }

  private void writeFileRandomlyApproximatelyTwice(final @NotNull File file,
                                                   final @NotNull ByteBuffer buffer) throws IOException {
    try (PagedFileStorage pagedStorage = new PagedFileStorage(file.toPath(), storageContext, BUFFER_SIZE, true, true)) {
      final ThreadLocalRandom rnd = ThreadLocalRandom.current();
      final long blocksCount = FILE_SIZE / BUFFER_SIZE;
      for (int i = 0; i < blocksCount * TWICE; i++) {
        final long blockNo = rnd.nextLong(blocksCount);
        final long blockOffset = blockNo * BUFFER_SIZE;
        pagedStorage.lockWrite();
        try {
          final DirectBufferWrapper page = pagedStorage.getByteBuffer(blockOffset, /*forWrite: */ true);
          try {
            // emulate 'write':
            buffer.clear();
            page.putFromBuffer(buffer, 0);
          }
          finally {
            page.unlock();
          }
        }
        finally {
          pagedStorage.unlockWrite();
        }
      }
    }
  }
}