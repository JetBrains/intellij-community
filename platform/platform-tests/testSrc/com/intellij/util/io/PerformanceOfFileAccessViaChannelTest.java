// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Measures throughput:
 * 1. read/write
 * 2. sequential/random
 * 3. via Channel
 * 4. single/multi-threaded (THREADS)
 * <p>
 * How to run:
 * 1. Better run in 'forking' mode, so each benchmark uses a dedicated JVM
 * (you'll need to remove @Ignore then)
 * 2. Better use FILE_SIZE >= RAM, so OS page cache is not able to fulfill all the requests.
 * But keep in mind: benchmark uses (FILE_SIZE * THREADS) of disk space.
 */
@FixMethodOrder(MethodSorters.JVM)
public class PerformanceOfFileAccessViaChannelTest extends PerformanceOfFileAccessBaseTest {

  /** Write/read each file more than once -- to be sure :) */
  private static final int TWICE = 2;
  private static final boolean USE_IDEMPOTENT_OPS = Boolean.getBoolean("PerformanceOfFileAccessViaChannelTest.USE_IDEMPOTENT_OPS");


  private final OpenChannelsCache cache = new OpenChannelsCache(32);

  //======================= Single-threaded: =======================

  @Test
  public void singleThreaded_Sequential_Read() throws IOException {
    final File file = createRandomContentFileOfSize(FILE_SIZE);
    final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

    final long startedAtNs = System.nanoTime();

    readFileSequentiallyTwice(file, buffer);

    final long finishedAtNs = System.nanoTime();

    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalReadBytes = TWICE * FILE_SIZE;
    printReportForThroughput("Read sequentially", totalReadBytes, elapsedNs);
  }

  @Test
  public void singleThreaded_Random_Read() throws IOException {
    final File file = createRandomContentFileOfSize(FILE_SIZE);
    final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

    final long startedAtNs = System.nanoTime();
    readFileRandomlyApproximatelyTwice(file, buffer);

    final long finishedAtNs = System.nanoTime();
    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalReadBytes = TWICE * FILE_SIZE;
    printReportForThroughput("Read randomly", totalReadBytes, elapsedNs);
  }

  @Test
  public void singleThreaded_Sequential_Write() throws IOException {
    final File file = createRandomContentFileOfSize(FILE_SIZE);

    final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

    final long startedAtNs = System.nanoTime();
    writeFileSequentiallyTwice(file, buffer);

    final long finishedAtNs = System.nanoTime();
    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalWrittenBytes = TWICE * FILE_SIZE;
    printReportForThroughput("Written sequentially", totalWrittenBytes, elapsedNs);
  }

  @Test
  public void singleThreaded_Random_Write() throws IOException {
    final File file = createRandomContentFileOfSize(FILE_SIZE);

    final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

    final long startedAtNs = System.nanoTime();

    writeFileRandomlyApproximatelyTwice(file, buffer);

    final long finishedAtNs = System.nanoTime();

    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalWrittenBytes = TWICE * FILE_SIZE;
    printReportForThroughput("Written randomly", totalWrittenBytes, elapsedNs);
  }

  //======================= Multi-threaded: =======================

  @Test
  public void multiThreaded_Uncontended_Sequential_Read() throws IOException, InterruptedException {
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
    printReportForThroughput("Read sequentially, " + THREADS + " threads, uncontended", totalReadBytes, elapsedNs);
  }

  @Test
  public void multiThreaded_Uncontended_Random_Read() throws IOException, InterruptedException {
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
    printReportForThroughput("Read randomly, " + THREADS + " threads, uncontended", totalReadBytes, elapsedNs);
  }

  @Test
  public void multiThreaded_Uncontended_Sequential_Write() throws IOException, InterruptedException {
    final Runnable[] tasks = new Runnable[THREADS];
    for (int taskNo = 0; taskNo < tasks.length; taskNo++) {
      final File file = createRandomContentFileOfSize(FILE_SIZE);
      final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);
      tasks[taskNo] = () -> {
        try {
          writeFileSequentiallyTwice(file, buffer);
        }
        catch (IOException e) {
          e.printStackTrace();
          throw new AssertionError(e);
        }
      };
    }

    final long startedAtNs = System.nanoTime();

    runThroughputTasksMultiThreaded(tasks);

    final long finishedAtNs = System.nanoTime();

    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalWrittenBytes = TWICE * FILE_SIZE * THREADS;
    printReportForThroughput("Written sequentially, " + THREADS + " threads, uncontended", totalWrittenBytes, elapsedNs);
  }

  @Test
  public void multiThreaded_Uncontended_Random_Write() throws IOException, InterruptedException {
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
    printReportForThroughput("Written randomly, " + THREADS + " threads, uncontended", totalWrittenBytes, elapsedNs);
  }


  // ================= infrastructure: =====================================================================


  private int writeBlockAtOffset(final File file,
                                 final long blockOffset,
                                 final ByteBuffer blockToWrite) throws IOException {
    if (USE_IDEMPOTENT_OPS) {
      return cache.executeIdempotentOp(file.toPath(), channel -> {
        return channel.write(blockToWrite, blockOffset);
      }, false);
    }
    else {
      return cache.executeOp(file.toPath(), channel -> {
        return channel.write(blockToWrite, blockOffset);
      }, false);
    }
  }

  private int readBlockAtOffset(final File file,
                                final long blockOffset,
                                final ByteBuffer blockToRead) throws IOException {
    if (USE_IDEMPOTENT_OPS) {
      return cache.executeIdempotentOp(file.toPath(), channel -> {
        return channel.read(blockToRead, blockOffset);
      }, false);
    }
    else {
      return cache.executeOp(file.toPath(), channel -> {
        return channel.read(blockToRead, blockOffset);
      }, false);
    }
  }


  private void readFileSequentiallyTwice(final @NotNull File file,
                                         final @NotNull ByteBuffer buffer) throws IOException {
    final long blocksCount = FILE_SIZE / BUFFER_SIZE;
    for (int turn = 0; turn < TWICE; turn++) {
      for (int blockNo = 0; blockNo < blocksCount; blockNo++) {
        final long blockOffset = blockNo * (long)BUFFER_SIZE;
        buffer.clear();
        final int bytesRead = readBlockAtOffset(file, blockOffset, buffer);
        assert bytesRead == BUFFER_SIZE : "bytesRead: " + bytesRead;
      }
    }
    cache.closeChannel(file.toPath());
  }

  private void readFileRandomlyApproximatelyTwice(final @NotNull File file,
                                                  final @NotNull ByteBuffer buffer) throws IOException {
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    final long blocksCount = FILE_SIZE / BUFFER_SIZE;
    for (int i = 0; i < blocksCount * TWICE; i++) {
      final long blockNo = rnd.nextLong(blocksCount);
      final long blockOffset = blockNo * BUFFER_SIZE;

      buffer.clear();
      final int bytesRead = readBlockAtOffset(file, blockOffset, buffer);
      assert bytesRead == BUFFER_SIZE : "bytesRead: " + bytesRead;
    }
    cache.closeChannel(file.toPath());
  }

  private void writeFileSequentiallyTwice(final @NotNull File file,
                                          final @NotNull ByteBuffer buffer) throws IOException {
    final long blocksCount = FILE_SIZE / BUFFER_SIZE;
    for (int turn = 0; turn < TWICE; turn++) {
      for (int blockNo = 0; blockNo < blocksCount; blockNo++) {
        final long blockOffset = blockNo * (long)BUFFER_SIZE;
        buffer.clear();
        final int bytesWritten = writeBlockAtOffset(file, blockOffset, buffer);
        assert bytesWritten == BUFFER_SIZE : "bytesWritten: " + bytesWritten;
      }
    }
    cache.closeChannel(file.toPath());
  }

  private void writeFileRandomlyApproximatelyTwice(final @NotNull File file,
                                                   final @NotNull ByteBuffer buffer) throws IOException {
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    final long blocksCount = FILE_SIZE / BUFFER_SIZE;
    for (int i = 0; i < blocksCount * TWICE; i++) {
      final long blockNo = rnd.nextLong(blocksCount);
      final long blockOffset = blockNo * BUFFER_SIZE;
      buffer.clear();
      final int bytesWritten = writeBlockAtOffset(file, blockOffset, buffer);
      assert bytesWritten == BUFFER_SIZE : "bytesWritten: " + bytesWritten;
    }
    cache.closeChannel(file.toPath());
  }
}