// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import static java.util.concurrent.TimeUnit.MINUTES;

import org.junit.*;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.*;

/**
 */
public class DirectByteBufferAllocatorTest {


  public static final int KEEP_ALIVE_BUFFERS_COUNT = 512;
  public static final int TRIES = 10_000_000;
  private DirectByteBufferAllocator allocator;

  @Before
  public void setUp() throws Exception {
    //TODO make dedicated allocator for test, to not interfere with other tests
    allocator = DirectByteBufferAllocator.ALLOCATOR;
  }

  @Test
  public void concurrentAllocationsAndDeallocationsDoesntFail() throws Exception {
    //IDEA-309792: my guess it is something with concurrent release of many buffers
    //TODO RC: issue seems to be specific for linux, waiting for linux VM to run test on it
    final int threads = Runtime.getRuntime().availableProcessors();
    final ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      final int[] pageSizes = {1024, 4096, 1 << 20};
      final Callable<Void> task = () -> {
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();
        final ArrayDeque<ByteBuffer> allocatedQueue = new ArrayDeque<>();

        for (int i = 0; i < KEEP_ALIVE_BUFFERS_COUNT; i++) {
          final int pageSize = pageSizes[rnd.nextInt(pageSizes.length)];
          final ByteBuffer allocated = allocator.allocate(pageSize);
          allocatedQueue.offer(allocated);
        }

        for (int i = 0; i < TRIES; i++) {
          final int pageSize = pageSizes[rnd.nextInt(pageSizes.length)];
          final ByteBuffer allocated = allocator.allocate(pageSize);
          allocatedQueue.offer(allocated);
          final ByteBuffer toRelease = allocatedQueue.poll();
          allocator.release(toRelease);
        }

        for (ByteBuffer buffer : allocatedQueue) {
          allocator.release(buffer);
        }
        return null;
      };

      for (int i = 0; i < threads; i++) {
        pool.submit(task);
      }
    }
    finally {
      pool.shutdown();
      pool.awaitTermination(10, MINUTES);
    }
  }
}