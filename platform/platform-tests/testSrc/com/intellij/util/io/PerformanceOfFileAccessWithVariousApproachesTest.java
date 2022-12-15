// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static com.intellij.util.io.IOUtil.GiB;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Measures throughput:
 * 1. read/write
 * 2. sequential/random
 * 3. via Channel/via FilePageCache
 * 4. single/multi-threaded (THREADS)
 * <p>
 * How to run:
 * 1. Better run in 'forking' mode, so each benchmark uses a dedicated JVM
 * (you'll need to remove @Ignore then)
 * 2. Better use FILE_SIZE >= RAM, so OS page cache is not able to fulfill all the requests.
 * But keep in mind: benchmark uses (FILE_SIZE * THREADS) of disk space.
 */
@Ignore("Benchmark, not test, hence ignored by default -> to run only explicitly")
public class PerformanceOfFileAccessWithVariousApproachesTest {

  private static final long FILE_SIZE = 1L << 34; //  16 Gb

  private static final int BUFFER_SIZE = 1 << 20; //  1 Mb

  private static final int THREADS = 5;           // = max 40 Gb of files

  /** Write/read each file more than once -- to be sure :) */
  private static final int TWICE = 2;

  @Rule
  public final TemporaryFolder tmpDirectory = new TemporaryFolder();


  private final OpenChannelsCache cache = new OpenChannelsCache(32);

  private final StorageLockContext storageContext = new StorageLockContext(true, true, true);

  //======================= Single-threaded: =======================

  @Test
  public void singleThreaded_Sequential_Write() throws IOException {
    final File file = createRandomContentFileOfSize(FILE_SIZE);

    final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

    final long startedAtNs = System.nanoTime();
    writeFileSequentiallyTwice(file, buffer);

    final long finishedAtNs = System.nanoTime();
    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalWrittenBytes = TWICE * FILE_SIZE;
    printReport("Written sequentially", totalWrittenBytes, elapsedNs);
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
    printReport("Written randomly", totalWrittenBytes, elapsedNs);
  }

  @Test
  public void singleThreaded_Sequential_Read() throws IOException {
    final File file = createRandomContentFileOfSize(FILE_SIZE);
    final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

    final long startedAtNs = System.nanoTime();

    readFileSequentiallyTwice(file, buffer);

    final long finishedAtNs = System.nanoTime();

    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalReadBytes = TWICE * FILE_SIZE;
    printReport("Read sequentially", totalReadBytes, elapsedNs);
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
    printReport("Read randomly", totalReadBytes, elapsedNs);
  }

  //======================= Single-threaded, with FilePageCache ========
  //======================= Single-threaded, with FilePageCache ========

  @Test
  public void singleThreaded_Random_Read_ViaFilePageCache() throws IOException {
    final File file = createRandomContentFileOfSize(FILE_SIZE);
    final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

    final long startedAtNs = System.nanoTime();

    readFileRandomlyApproximatelyTwice_WithPageCache(file, buffer);

    final long finishedAtNs = System.nanoTime();
    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalReadBytes = TWICE * FILE_SIZE;
    printReport("Read randomly, with PageCache", totalReadBytes, elapsedNs);
  }

  @Test
  public void singleThreaded_Sequential_Read_ViaFilePageCache() throws IOException {
    final File file = createRandomContentFileOfSize(FILE_SIZE);
    final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

    final long startedAtNs = System.nanoTime();

    readFileSequentiallyTwice_WithPageCache(file, buffer);

    final long finishedAtNs = System.nanoTime();
    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalReadBytes = TWICE * FILE_SIZE;
    printReport("Read sequentially, with PageCache", totalReadBytes, elapsedNs);
  }

  @Test
  public void singleThreaded_Random_Write_ViaFilePageCache() throws IOException {
    final File file = createRandomContentFileOfSize(FILE_SIZE);
    final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

    final long startedAtNs = System.nanoTime();

    writeFileRandomlyApproximatelyTwice_ViaPageCache(file, buffer);

    final long finishedAtNs = System.nanoTime();
    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalReadBytes = TWICE * FILE_SIZE;
    printReport("Write randomly, with PageCache", totalReadBytes, elapsedNs);
  }

  @Test
  public void singleThreaded_Sequential_Write_ViaFilePageCache() throws IOException {
    final File file = createRandomContentFileOfSize(FILE_SIZE);
    final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

    final long startedAtNs = System.nanoTime();

    writeFileSequentiallyTwice_ViaPageCache(file, buffer);

    final long finishedAtNs = System.nanoTime();

    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalReadBytes = TWICE * FILE_SIZE;
    printReport("Write sequentially, with PageCache", totalReadBytes, elapsedNs);
  }

  //======================= Multi-threaded, with FilePageCache ========

  @Test
  public void multiThreaded_Uncontended_Sequential_Read_ViaPageCache() throws IOException, InterruptedException {
    final ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    final Runnable[] tasks = new Runnable[THREADS];
    for (int threadNo = 0; threadNo < THREADS; threadNo++) {
      final File file = createRandomContentFileOfSize(FILE_SIZE);
      final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

      tasks[threadNo] = () -> {
        try {
          readFileSequentiallyTwice_WithPageCache(file, buffer);
        }
        catch (IOException e) {
          throw new AssertionError(e);
        }
      };
    }

    final long startedAtNs = System.nanoTime();

    for (final Runnable task : tasks) {
      pool.execute(task);
    }
    pool.shutdown();
    pool.awaitTermination(1, MINUTES);

    final long finishedAtNs = System.nanoTime();

    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalReadBytes = TWICE * FILE_SIZE * THREADS;
    printReport("Read sequentially, " + THREADS + " threads, uncontended, via PageCache", totalReadBytes, elapsedNs);
  }

  @Test
  public void multiThreaded_Uncontended_Sequential_Write_ViaPageCache() throws IOException, InterruptedException {
    final ExecutorService pool = Executors.newFixedThreadPool(THREADS);

    final List<Callable<Void>> tasks = new ArrayList<>();
    for (int threadNo = 0; threadNo < THREADS; threadNo++) {
      final File file = createRandomContentFileOfSize(FILE_SIZE);
      final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);
      tasks.add(() -> {
        try {
          writeFileSequentiallyTwice_ViaPageCache(file, buffer);
          return null;
        }
        catch (IOException e) {
          e.printStackTrace();
          throw new AssertionError(e);
        }
      });
    }

    final long startedAtNs = System.nanoTime();

    pool.invokeAll(tasks);
    pool.shutdown();
    pool.awaitTermination(1, MINUTES);

    final long finishedAtNs = System.nanoTime();

    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalWrittenBytes = TWICE * FILE_SIZE * THREADS;
    printReport("Written sequentially, " + THREADS + " threads, uncontended, via PageCache", totalWrittenBytes, elapsedNs);
  }

  @Test
  public void multiThreaded_Uncontended_Random_Read_ViaPageCache() throws IOException, InterruptedException {
    final ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    final Runnable[] tasks = new Runnable[THREADS];
    for (int threadNo = 0; threadNo < THREADS; threadNo++) {
      final File file = createRandomContentFileOfSize(FILE_SIZE);
      final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

      tasks[threadNo] = () -> {
        try {
          readFileRandomlyApproximatelyTwice_WithPageCache(file, buffer);
        }
        catch (IOException e) {
          throw new AssertionError(e);
        }
      };
    }

    final long startedAtNs = System.nanoTime();

    for (final Runnable task : tasks) {
      pool.execute(task);
    }
    pool.shutdown();
    pool.awaitTermination(1, MINUTES);

    final long finishedAtNs = System.nanoTime();

    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalReadBytes = TWICE * FILE_SIZE * THREADS;
    printReport("Read randomly, " + THREADS + " threads, uncontended, via PageCache", totalReadBytes, elapsedNs);
  }

  @Test
  public void multiThreaded_Uncontended_Random_Write_ViaPageCache() throws IOException, InterruptedException {
    final ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    final Runnable[] tasks = new Runnable[THREADS];
    for (int threadNo = 0; threadNo < THREADS; threadNo++) {
      final File file = createRandomContentFileOfSize(FILE_SIZE);
      final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

      tasks[threadNo] = () -> {
        try {
          writeFileRandomlyApproximatelyTwice_ViaPageCache(file, buffer);
        }
        catch (IOException e) {
          throw new AssertionError(e);
        }
      };
    }

    final long startedAtNs = System.nanoTime();

    for (final Runnable task : tasks) {
      pool.execute(task);
    }
    pool.shutdown();
    pool.awaitTermination(1, MINUTES);

    final long finishedAtNs = System.nanoTime();

    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalWrittenBytes = TWICE * FILE_SIZE * THREADS;
    printReport("Written randomly, " + THREADS + " threads, uncontended, via PageCache", totalWrittenBytes, elapsedNs);
  }


  //======================= Multi-threaded: =======================

  @Test
  public void multiThreaded_Uncontended_Sequential_Read() throws IOException, InterruptedException {
    final ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    final Runnable[] tasks = new Runnable[THREADS];
    for (int threadNo = 0; threadNo < THREADS; threadNo++) {
      final File file = createRandomContentFileOfSize(FILE_SIZE);
      final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

      tasks[threadNo] = () -> {
        try {
          readFileSequentiallyTwice(file, buffer);
        }
        catch (IOException e) {
          throw new AssertionError(e);
        }
      };
    }

    final long startedAtNs = System.nanoTime();

    for (final Runnable task : tasks) {
      pool.execute(task);
    }
    pool.shutdown();
    pool.awaitTermination(1, MINUTES);

    final long finishedAtNs = System.nanoTime();

    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalReadBytes = TWICE * FILE_SIZE * THREADS;
    printReport("Read sequentially, " + THREADS + " threads, uncontended", totalReadBytes, elapsedNs);
  }

  @Test
  public void multiThreaded_Uncontended_Sequential_Write() throws IOException, InterruptedException {
    final ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    final Runnable[] tasks = new Runnable[THREADS];
    for (int threadNo = 0; threadNo < THREADS; threadNo++) {
      final File file = createRandomContentFileOfSize(FILE_SIZE);
      final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

      tasks[threadNo] = () -> {
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

    for (final Runnable task : tasks) {
      pool.execute(task);
    }
    pool.shutdown();
    pool.awaitTermination(1, MINUTES);

    final long finishedAtNs = System.nanoTime();

    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalWrittenBytes = TWICE * FILE_SIZE * THREADS;
    printReport("Written sequentially, " + THREADS + " threads, uncontended", totalWrittenBytes, elapsedNs);
  }


  @Test
  public void multiThreaded_Uncontended_Random_Read() throws IOException, InterruptedException {
    final ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    final Runnable[] tasks = new Runnable[THREADS];
    for (int threadNo = 0; threadNo < THREADS; threadNo++) {
      final File file = createRandomContentFileOfSize(FILE_SIZE);
      final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

      tasks[threadNo] = () -> {
        try {
          readFileRandomlyApproximatelyTwice(file, buffer);
        }
        catch (IOException e) {
          throw new AssertionError(e);
        }
      };
    }

    final long startedAtNs = System.nanoTime();

    for (final Runnable task : tasks) {
      pool.execute(task);
    }
    pool.shutdown();
    pool.awaitTermination(1, MINUTES);

    final long finishedAtNs = System.nanoTime();

    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalReadBytes = TWICE * FILE_SIZE * THREADS;
    printReport("Read randomly, " + THREADS + " threads, uncontended", totalReadBytes, elapsedNs);
  }

  @Test
  public void multiThreaded_Uncontended_Random_Write() throws IOException, InterruptedException {
    final ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    final Runnable[] tasks = new Runnable[THREADS];
    for (int threadNo = 0; threadNo < THREADS; threadNo++) {
      final File file = createRandomContentFileOfSize(FILE_SIZE);
      final ByteBuffer buffer = randomContentBufferOfSize(BUFFER_SIZE);

      tasks[threadNo] = () -> {
        try {
          writeFileRandomlyApproximatelyTwice(file, buffer);
        }
        catch (IOException e) {
          throw new AssertionError(e);
        }
      };
    }

    final long startedAtNs = System.nanoTime();

    for (final Runnable task : tasks) {
      pool.execute(task);
    }
    pool.shutdown();
    pool.awaitTermination(1, MINUTES);

    final long finishedAtNs = System.nanoTime();

    final long elapsedNs = finishedAtNs - startedAtNs;
    final long totalWrittenBytes = TWICE * FILE_SIZE * THREADS;
    printReport("Written randomly, " + THREADS + " threads, uncontended", totalWrittenBytes, elapsedNs);
  }


  // ================= infrastructure: =====================================================================


  private static void printReport(final String caption,
                                  final long totalBytes,
                                  final long elapsedNs) {
    System.out.printf("%60s: %3.1f GiB / %5d ms = %.2f GiB/s\n",
                      caption,
                      (totalBytes * 1.0) / GiB,
                      NANOSECONDS.toMillis(elapsedNs),
                      (totalBytes * 1.0) / GiB / (NANOSECONDS.toMillis(elapsedNs) / 1000.0)
    );
  }

  private File createRandomContentFileOfSize(final long fileSize) throws IOException {
    final File file = tmpDirectory.newFile();

    final byte[] randomBytes = randomArray(BUFFER_SIZE);
    final long batches = fileSize / BUFFER_SIZE;
    try (final RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
      raf.setLength(fileSize);
      raf.seek(0);
      for (int i = 0; i < batches; i++) {
        raf.write(randomBytes);
      }
      raf.getChannel().force(true);
    }
    return file;
  }

  @NotNull
  private static ByteBuffer randomContentBufferOfSize(final int size) {
    final byte[] array = randomArray(size);
    return ByteBuffer.wrap(array);
  }

  @NotNull
  private static byte[] randomArray(final int size) {
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    final byte[] array = new byte[size];
    for (int i = 0; i < array.length; i++) {
      array[i] = (byte)rnd.nextInt(Byte.MIN_VALUE, Byte.MAX_VALUE + 1);
    }
    return array;
  }


  private int writeBlockAtOffset(final File file,
                                 final long blockOffset,
                                 final ByteBuffer blockToWrite) throws IOException {
    return cache.useChannel(file.toPath(), channel -> {
      return channel.write(blockToWrite, blockOffset);
    }, false);
  }

  private int readBlockAtOffset(final File file,
                                final long blockOffset,
                                final ByteBuffer blockToRead) throws IOException {
    return cache.useChannel(file.toPath(), channel -> {
      return channel.read(blockToRead, blockOffset);
    }, false);
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


  private void readFileSequentiallyTwice_WithPageCache(final @NotNull File file,
                                                       final @NotNull ByteBuffer buffer) throws IOException {
    final PagedFileStorage pagedStorage = new PagedFileStorage(file.toPath(), storageContext, BUFFER_SIZE, true, true);
    try {
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
              buffer.put(page.getBuffer().duplicate());
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
      cache.closeChannel(file.toPath());
    }
    finally {
      pagedStorage.close();
    }
  }

  private void readFileRandomlyApproximatelyTwice_WithPageCache(final @NotNull File file,
                                                                final @NotNull ByteBuffer buffer) throws IOException {
    final PagedFileStorage pagedStorage = new PagedFileStorage(file.toPath(), storageContext, BUFFER_SIZE, true, true);
    try {
      final ThreadLocalRandom rnd = ThreadLocalRandom.current();
      final long blocksCount = FILE_SIZE / BUFFER_SIZE;
      for (int i = 0; i < blocksCount * TWICE; i++) {
        final long blockNo = rnd.nextLong(blocksCount);
        final long blockOffset = blockNo * BUFFER_SIZE;
        pagedStorage.lockRead();
        try {
          final DirectBufferWrapper page = pagedStorage.getByteBuffer(blockOffset, /*forWrite: */ false);
          try {
            // emulate 'read':
            buffer.clear();
            buffer.put(page.getBuffer().duplicate());
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
    finally {
      pagedStorage.close();
    }
  }

  private void writeFileSequentiallyTwice_ViaPageCache(final @NotNull File file,
                                                       final @NotNull ByteBuffer buffer) throws IOException {
    final PagedFileStorage pagedStorage = new PagedFileStorage(file.toPath(), storageContext, BUFFER_SIZE, true, true);
    try {
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
      cache.closeChannel(file.toPath());
    }
    finally {
      pagedStorage.close();
    }
  }

  private void writeFileRandomlyApproximatelyTwice_ViaPageCache(final @NotNull File file,
                                                                final @NotNull ByteBuffer buffer) throws IOException {
    final PagedFileStorage pagedStorage = new PagedFileStorage(file.toPath(), storageContext, BUFFER_SIZE, true, true);
    try {
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
    finally {
      pagedStorage.close();
    }
  }
}