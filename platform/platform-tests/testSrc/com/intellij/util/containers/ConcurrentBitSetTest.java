// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.system.CpuArch;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrentBitSetTest {
  private static final Logger LOG = Logger.getInstance(ConcurrentBitSetTest.class);

  @Test
  void testSanity() {
    ConcurrentBitSet bitSet = ConcurrentBitSet.create();
    assertEquals(0, bitSet.nextClearBit(0));
    assertEquals(-1, bitSet.nextSetBit(0));
    int N = 3000;
    for (int i = 0; i < N; i++) {
      assertEquals(-1, bitSet.nextSetBit(i));
      assertEquals(i, bitSet.nextClearBit(i));
      assertFalse(bitSet.get(i));
      bitSet.set(i);
      assertTrue(bitSet.get(i));
      bitSet.clear(i);
      assertFalse(bitSet.get(i));
      assertEquals(-1, bitSet.nextSetBit(0));
    }
    bitSet = ConcurrentBitSet.create();
    for (int b=0;b<N;b++) {
      assertEquals(-1, bitSet.nextSetBit(0));
      boolean prev = bitSet.set(b);
      assertFalse(prev);
      assertEquals(b, bitSet.nextSetBit(0));
      assertEquals(b==0?1:0, bitSet.nextClearBit(0));
      assertEquals(b+1, bitSet.nextClearBit(b));
      assertFalse(bitSet.get(b==0?1:0));
      assertTrue(bitSet.get(b));
      for (int i=0; i<N;i++) {
        assertEquals(i<=b?b:-1, bitSet.nextSetBit(i));
        assertEquals(i==b?b+1:i, bitSet.nextClearBit(i));
        assertEquals(i == b, bitSet.get(i));
      }

      boolean prev2 = bitSet.clear(b);
      assertTrue(prev2);
      assertEquals(-1, bitSet.nextSetBit(0));
      assertEquals(0, bitSet.nextClearBit(0));
      assertEquals(b, bitSet.nextClearBit(b));
      assertFalse(bitSet.get(0));
      assertFalse(bitSet.get(b));
      for (int i=0; i<N;i++) {
        assertEquals(-1, bitSet.nextSetBit(i));
        assertEquals(i, bitSet.nextClearBit(i));
        assertFalse(bitSet.get(i));
      }
    }
  }

  @Test
  void testStressFineGrainedSmallSetModifications() {
    PlatformTestUtil.assumeEnoughParallelism();
    int L = 128;
    int N = 10_000;
    Benchmark.newBenchmark("testStressFineGrainedSmallSetModifications", () -> tortureParallelSetClear(L, N))
      .runAsStressTest()
      .start();
  }

  @Test
  void testStressCoarseGrainedBigSet() {
    PlatformTestUtil.assumeEnoughParallelism();
    int L = 100_000;
    // todo ARM64 is slow for some reason
    int N = CpuArch.isArm64() ? 300 : 1000;

    Benchmark.newBenchmark("testStressCoarseGrainedBigSet", () -> tortureParallelSetClear(L, N))
      .runAsStressTest()
      .start();
  }

  private static void tortureParallelSetClear(int L, int N) {
    boolean[] indexMask = new boolean[L];
    int[] indicesToSet = new Random().ints(L / 2, 0, L).peek(i->indexMask[i]=true).toArray();
    long distinctIndexNumber = Arrays.stream(indicesToSet).distinct().count();
    ExecutorService executor = create4ThreadsExecutor();
    try {
      Set<Thread> threadUsed = ConcurrentCollectionFactory.createConcurrentSet();
      Semaphore threadReady = new Semaphore();
      for (int it = 0; it < N; it++) {
        ConcurrentBitSet bitSet = ConcurrentBitSet.create();
        assertEquals(-1, bitSet.nextSetBit(0));
        boundedParallelRun(executor, threadUsed, threadReady, L, i -> {
          bitSet.set(i);
          assertTrue(bitSet.get(i));
        });
        assertEquals(L, bitSet.nextClearBit(0));
        boundedParallelRun(executor, threadUsed, threadReady, L, i -> {
          bitSet.clear(i);
          assertFalse(bitSet.get(i));
        });
        assertEquals(-1, bitSet.nextSetBit(0));

        boundedParallelRun(executor, threadUsed, threadReady, L / 2, i -> bitSet.set(indicesToSet[i]));
        assertEquals(distinctIndexNumber, bitSet.cardinality());
        boundedParallelRun(executor, threadUsed, threadReady, L, i -> {
          assertEquals(indexMask[i], bitSet.get(i));
          bitSet.set(i);
        });
        boundedParallelRun(executor, threadUsed, threadReady, L / 2, i -> bitSet.clear(indicesToSet[i]));
        assertEquals(distinctIndexNumber, L-bitSet.cardinality());
        boundedParallelRun(executor, threadUsed, threadReady, L, i -> {
          assertEquals(!indexMask[i], bitSet.get(i));
        });
        bitSet.clear();
        assertEquals(-1, bitSet.nextSetBit(0));
      }
      System.out.println("threadUsed = " + threadUsed);
    }
    finally {
      executor.shutdownNow();
    }
  }

  @NotNull
  private static ExecutorService create4ThreadsExecutor() {
    // because TC usually has no more than 4 cores
    AtomicInteger cnt = new AtomicInteger();
    return new ThreadPoolExecutor(4, 4, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                                  r -> new Thread(r, "ConcurrentBitSetTest.tortureParallelSetClear-"+cnt.getAndIncrement()));
  }

  // feeds indices [0..L) to the consumer in parallel, with parallelism = exactly 4, to make tests run more/less uniformly, locally and on TC
  static void boundedParallelRun(ExecutorService executor, Set<? super Thread> threadUsed, Semaphore threadReady, int L, IntConsumer index) {
    assert L % 4 == 0;
    threadUsed.clear();
    threadReady.down();threadReady.down();threadReady.down();threadReady.down();
    List<? extends Future<?>> futures = IntStream.range(0, 4).mapToObj(chunk ->
     executor.submit(() -> {
      threadUsed.add(Thread.currentThread());
      threadReady.up();
      threadReady.waitFor();
      for (int i = L / 4 * chunk; i < L / 4 * chunk + L / 4; i++) {
        index.accept(i);
      }
    })).toList();
    try {
      ConcurrencyUtil.getAll(futures);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    assertTrue(threadReady.isUp());
    assertEquals(4, threadUsed.size(), threadUsed.size() + " :\n" + StringUtil.join(threadUsed, "\n"));
  }

  @Test
  void testParallelReadPerformance() {
    PlatformTestUtil.assumeEnoughParallelism();
    int len = 100_000;
    ConcurrentBitSet set = ConcurrentBitSet.create();
    Random random = new Random();
    int s = 0;
    for (int i = 0; i < len; i++) {
      set.set(i, random.nextBoolean());
      s += set.get(i)?1:0;
    }
    int expectedSum = s;

    int N = 100_000;

    ExecutorService executor = create4ThreadsExecutor();
    Benchmark.newBenchmark("testParallelReadPerformance", ()-> {
      Semaphore threadReady = new Semaphore();
      Set<Thread> threadUsed = ConcurrentCollectionFactory.createConcurrentSet();
      boundedParallelRun(executor, threadUsed, threadReady, N, __-> {
        int r = 0;
        for (int j = 0; j < len; j++) {
          r += set.get(j)?1:0;
        }
        assertEquals(expectedSum, r);
      });
    // really must not depend on CPU core number
    }).runAsStressTest()
      .start();
  }

  @Test
  void testSetPerformance() {
    int len = 100000;
    ConcurrentBitSet set = ConcurrentBitSet.create();
    Random random = new Random();
    for (int i = 0; i < len; i++) {
      set.set(i, random.nextBoolean());
    }

    // must be even
    int N = 1000;

    for (int i=0; i<10; i++) {
      long el = TimeoutUtil.measureExecutionTime(() -> {
        int r = 0;
        for (int n = 0; n < N; n++) {
          for (int j = 0; j < len; j++) {
            int prev = set.get(j)?1:0;
            r+=prev;
            set.set(j, prev==0);
          }
        }
        assertEquals(N/2 * len, r);
      });

      LOG.debug("elapsed = " + el);
    }
  }
}
