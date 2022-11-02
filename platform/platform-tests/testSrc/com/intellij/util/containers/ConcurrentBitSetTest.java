/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.containers;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.system.CpuArch;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
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
    int N = 100_000;
    PlatformTestUtil.startPerformanceTest("testStressFineGrainedSmallSetModifications", 80_000, () -> tortureParallelSetClear(L, N)).assertTiming();
  }

  @Test
  void testStressCoarseGrainedBigSet() {
    PlatformTestUtil.assumeEnoughParallelism();
    int L = 100_000;
    // todo ARM64 is slow for some reason
    int N = CpuArch.isArm64() ? 300 : 1000;

    PlatformTestUtil.startPerformanceTest("testStressCoarseGrainedBigSet", 80_000, () -> tortureParallelSetClear(L, N)).assertTiming();
  }

  private static void tortureParallelSetClear(int L, int N) {
    boolean[] indexMask = new boolean[L];
    int[] indicesToSet = new Random().ints(L / 2, 0, L).peek(i->indexMask[i]=true).toArray();
    long distinctIndexNumber = Arrays.stream(indicesToSet).distinct().count();
    ExecutorService executor = create4ThreadsExecutor();
    try {
      IntStream.range(0, N).forEach(__-> {
        ConcurrentBitSet bitSet = ConcurrentBitSet.create();
        assertEquals(-1, bitSet.nextSetBit(0));
        boundedParallelRun(executor, L, i -> {
          bitSet.set(i);
          assertTrue(bitSet.get(i));
        });
        assertEquals(L, bitSet.nextClearBit(0));
        boundedParallelRun(executor, L, i -> {
          bitSet.clear(i);
          assertFalse(bitSet.get(i));
        });
        assertEquals(-1, bitSet.nextSetBit(0));

        boundedParallelRun(executor, L / 2, i -> bitSet.set(indicesToSet[i]));
        assertEquals(distinctIndexNumber, bitSet.cardinality());
        boundedParallelRun(executor, L, i -> {
          assertEquals(indexMask[i], bitSet.get(i));
          bitSet.set(i);
        });
        boundedParallelRun(executor, L / 2, i -> bitSet.clear(indicesToSet[i]));
        assertEquals(distinctIndexNumber, L-bitSet.cardinality());
        boundedParallelRun(executor, L, i -> {
          assertEquals(!indexMask[i], bitSet.get(i));
        });
        bitSet.clear();
      });
    }
    finally {
      executor.shutdownNow();
    }
  }

  @NotNull
  private static ExecutorService create4ThreadsExecutor() {
    // because TC usually has no more than 4 cores
    return new ThreadPoolExecutor(4, 4, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
                                  ConcurrencyUtil.newNamedThreadFactory("com.intellij.util.containers.ConcurrentBitSetTest.tortureParallelSetClear"));
  }

  // feeds indices [0..L) to consumer in parallel with parallelism = exactly 4, to make tests run more/less uniformly, locally and on TC
  static void boundedParallelRun(ExecutorService executor, int L, IntConsumer index) {
    assert L % 4 == 0;
    List<? extends Future<?>> futures = IntStream.range(0, 4).mapToObj(chunk ->
      executor.submit(() -> {
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
    PlatformTestUtil.startPerformanceTest("testParallelReadPerformance", 35_000, ()-> {
      boundedParallelRun(executor, N, __-> {
        int r = 0;
        for (int j = 0; j < len; j++) {
          r += set.get(j)?1:0;
        }
        assertEquals(expectedSum, r);
      });
    // really must not depend on CPU core number
    })/*.usesAllCPUCores()*/.assertTiming();
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
