// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.*;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.*;
import java.util.stream.IntStream;

public class DirectByteBufferAllocatorTest {


  private DirectByteBufferAllocator allocator;

  @Before
  public void setUp() throws Exception {
    //TODO make dedicated allocator for test, to not interfere with other tests
    allocator = DirectByteBufferAllocator.ALLOCATOR;
  }

  @Test
  public void allocatorReturnsBufferOfCapacityNoMoreThanTwiceRequested() {
    int[] bufferSizesToCheck = generateBufferSizesToCheck();

    //intentionally request randomly to check different ordering:
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int i = 0; i < bufferSizesToCheck.length * 2; i++) {
      int bufferSize = bufferSizesToCheck[rnd.nextInt(bufferSizesToCheck.length)];
      ByteBuffer buffer = allocator.allocate(bufferSize);
      try {
        assertTrue(
          "Allocated buffer.capacity(=" + buffer.capacity() + ") must be <= twice requested size(=" + bufferSize + ")",
          buffer.capacity() <= bufferSize * 2
        );
      }
      finally {
        allocator.release(buffer);
      }
    }
  }

  @Test
  public void allocatorReturnsBufferWithRequestedFreeSpace() {
    int[] bufferSizesToCheck = generateBufferSizesToCheck();

    //intentionally request randomly to check different ordering:
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int i = 0; i < bufferSizesToCheck.length * 2; i++) {
      int bufferSize = bufferSizesToCheck[rnd.nextInt(bufferSizesToCheck.length)];
      ByteBuffer buffer = allocator.allocate(bufferSize);
      try {
        assertEquals(
          "Allocated buffer must be already positioned so that remaining=bufferSize",
          bufferSize,
          buffer.remaining()
        );
      }
      finally {
        allocator.release(buffer);
      }
    }
  }


  @Test
  public void concurrentAllocationsAndDeAllocationsDoesntFail() throws Exception {
    //IDEA-309792: Check the guess it is something with concurrent release of many buffers
    //             (issue seems to be specific for linux)
    //
    //             Test runs #CPU threads, each thread constantly allocates and releases buffers
    //             of various sizes, but there is always a gap between allocated and released buffers
    //             so that each thread 'owns' keepBuffersOwnedPerThread buffers at every moment (except
    //             init and shutdown). It should put enough pressure on concurrent allocation/release
    //             paths for bugs to rise and shine.

    int threads = Runtime.getRuntime().availableProcessors();
    ExecutorService pool = Executors.newFixedThreadPool(threads);

    //each thread keeps that number of buffers not-yet-released
    int keepBuffersOwnedPerThread = 512;
    int iterationsPerThread = 10_000_000;

    try {
      int[] pageSizes = {1024, 4096, 1 << 20};
      Callable<Void> task = () -> {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        ArrayDeque<ByteBuffer> keepAliveBuffersQueue = new ArrayDeque<>();

        for (int i = 0; i < keepBuffersOwnedPerThread; i++) {
          int pageSize = pageSizes[rnd.nextInt(pageSizes.length)];
          ByteBuffer allocated = allocator.allocate(pageSize);
          keepAliveBuffersQueue.offer(allocated);
        }

        //on each iteration, keepAliveBuffersPerThread buffers remains in the queue:
        for (int i = 0; i < iterationsPerThread; i++) {
          int pageSize = pageSizes[rnd.nextInt(pageSizes.length)];

          ByteBuffer allocated = allocator.allocate(pageSize);
          keepAliveBuffersQueue.offer(allocated);

          ByteBuffer toRelease = keepAliveBuffersQueue.poll();
          allocator.release(toRelease);
        }

        for (ByteBuffer buffer : keepAliveBuffersQueue) {
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


  private static int[] generateBufferSizesToCheck() {
    //size: 1, 2, 4, 8, .. 2^24
    return IntStream.rangeClosed(0, 24)
      .map(pow2 -> (1 << pow2))
      .toArray();
  }
}