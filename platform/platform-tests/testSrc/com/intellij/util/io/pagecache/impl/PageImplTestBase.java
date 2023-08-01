// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache.impl;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.junit.Assert.*;

public abstract class PageImplTestBase<P extends PageImpl> {

  /** All available CPUs -- except 1 which runs 'main' anyway */
  public static final int THREADS = Runtime.getRuntime().availableProcessors() - 1;

  public static final int ENOUGH_TRIES = 100_000;

  protected abstract P createPage(int pageIndex,
                                  int pageSize,
                                  @NotNull PageToStorageHandle pageToStorageHandle);

  @Test
  public void dirtyRegionUpdatesDoesNotMissUpdatesUnderConcurrentExecution() throws Exception {
    final int blockSize = 32;
    final int blocksPerThread = 8;
    final int threadsCount = 2;

    final int pageSize = blockSize * threadsCount * blocksPerThread;
    final byte[] backingStorage = new byte[pageSize];

    final byte[] block = randomByteArrayOfSize(blockSize);

    final PageToStorageHandle handle = new PageToStorageHandleHelper() {
      @Override
      public void flushBytes(final @NotNull ByteBuffer dataToFlush,
                             final long offsetInFile) {
        dataToFlush.get(backingStorage,
                        (int)offsetInFile, dataToFlush.remaining());
      }
    };

    final PageImpl page = createPage(0, pageSize, handle);
    assertTrue(
      "Preparation must succeed since there are no contenders",
      page.tryPrepareForUse(_page -> ByteBuffer.allocate(_page.pageSize()))
    );
    page.tryAcquireForUse(this);

    final Collection<Callable<Void>> tasks = IntStream.range(0, threadsCount)
      .mapToObj(threadNo -> (Callable<Void>)() -> {
                  //each thread writes blocksPerThread blocks, but starting with own offset, so
                  // blocks of different threads do not overlap
                  for (int blockNo = 0; blockNo < blocksPerThread; blockNo++) {
                    final int offsetOnPage = ((blockNo * threadsCount) + threadNo) * blockSize;
                    page.write(offsetOnPage, blockSize,
                               pageBuffer -> pageBuffer.put(block));
                  }
                  return null;
                }
      ).toList();


    final ExecutorService pool = Executors.newFixedThreadPool(threadsCount);
    try {
      for (int turn = 0; turn < ENOUGH_TRIES; turn++) {
        final List<Future<Void>> futures = ContainerUtil.map(tasks, pool::submit);
        do {
          page.flush();
        }
        while (!futures.stream().allMatch(Future::isDone));

        page.flush();

        //assert backingStorage is filled with repeating 'block' bytes:
        for (int blockNo = 0; blockNo < blocksPerThread * threadsCount; blockNo++) {
          final int startOffset = blockNo * blockSize;
          final byte[] _block = Arrays.copyOfRange(backingStorage, startOffset, startOffset + blockSize);
          if (!Arrays.equals(block, _block)) {
            fail(
              "turn[" + turn + "], block[#" + blockNo + "].range[" + startOffset + ".." + (startOffset + blockSize) + "): \n" +
              "expected: " + IOUtil.toHexString(block) + "\n" +
              " but was: " + IOUtil.toHexString(_block) + "\n"
            );
          }
        }
        //clean backingStorage before next turn:
        Arrays.fill(backingStorage, (byte)0);
      }
    }
    finally {
      pool.shutdown();
      pool.awaitTermination(10, MINUTES);
    }
  }

  @Test
  public void pageAndStorageDirtyStatusesAreConsistent_EvenWithConcurrentFlushes() throws Exception {

    final DirtyStatusCounter dirtinessCounter = new DirtyStatusCounter();
    final PageImpl page = createPage(0, 1024, dirtinessCounter);
    assertTrue(
      "Preparation must succeed since there are no contenders",
      page.tryPrepareForUse(_page -> ByteBuffer.allocate(_page.pageSize()))
    );
    page.tryAcquireForUse(this);

    final ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    try {
      final Callable<Void> flushingTask = () -> {
        page.flush();

        assertEquals(
          "Dirty pages must be 0 since the only page is just successfully .flush()-ed",
          0,
          dirtinessCounter.dirtyPagesCount()
        );

        return null;
      };
      final List<Callable<Void>> flushingTasks = new ArrayList<>();
      for (int i = 0; i < THREADS; i++) {
        flushingTasks.add(flushingTask);
      }

      for (int turn = 0; turn < ENOUGH_TRIES; turn++) {
        page.putLong(0, Long.MAX_VALUE);    //make the page dirty

        final List<Future<Void>> futures = pool.invokeAll(flushingTasks);

        assertEquals("Dirty pages must be 0 since the only page is just successfully .flush()-ed",
                     0,
                     dirtinessCounter.dirtyPagesCount()
        );

        for (final Future<Void> future : futures) {
          try {
            future.get();
          }
          catch (ExecutionException e) {
            throw new AssertionError("Turn[#" + turn + "] ", e.getCause());
          }
        }
      }
    }
    finally {
      pool.shutdown();
      assertTrue(
        "Pool must terminate in 10 min",
        pool.awaitTermination(10, MINUTES)
      );
    }
  }

  //TODO RC: test state-transition diagram for a Page: prepare Page at specific (state, useCount), and
  //         apply different transition methods

  // =================== infrastructure: ============================================================================ //

  private static byte[] randomByteArrayOfSize(int length) {
    final byte[] array = new byte[length];
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int i = 0; i < array.length; i++) {
      array[i] = (byte)rnd.nextInt(Byte.MIN_VALUE, Byte.MAX_VALUE);
    }
    return array;
  }

  private static class DirtyStatusCounter extends PageToStorageHandleHelper {
    private final AtomicInteger dirtyPagesCount = new AtomicInteger(0);

    @Override
    public void pageBecomeDirty() {
      final int dirtyPages = dirtyPagesCount.incrementAndGet();
      if (dirtyPages != 1) {
        throw new AssertionError("Should be only one dirty page: " + dirtyPages);
      }
    }

    @Override
    public void pageBecomeClean() {
      final int dirtyPages = dirtyPagesCount.decrementAndGet();
      if (dirtyPages != 0) {
        throw new AssertionError("Should be 0 dirty pages: " + dirtyPages);
      }
    }

    public int dirtyPagesCount() {
      return dirtyPagesCount.get();
    }
  }
}
