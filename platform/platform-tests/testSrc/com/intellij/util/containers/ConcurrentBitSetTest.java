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
import com.intellij.testFramework.Timings;
import com.intellij.util.TimeoutUtil;
import junit.framework.TestCase;

import java.util.Random;
import java.util.stream.IntStream;

public class ConcurrentBitSetTest extends TestCase {
  private static final Logger LOG = Logger.getInstance(ConcurrentBitSetTest.class);
  public void testSanity() {
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
      boolean set = bitSet.flip(b);
      assertTrue(set);
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

      boolean after = bitSet.flip(b);
      assertFalse(after);
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
    bitSet.set(100, true);
    assertEquals(100, bitSet.nextSetBit(0));

    bitSet.clear();
    assertEquals(-1, bitSet.nextSetBit(0));
  }

  public void testStressFineGrainedSmallSet() {
    final ConcurrentBitSet bitSet = ConcurrentBitSet.create();
    // must be even
    int N = Timings.adjustAccordingToMySpeed(100_000, true) / 2 * 2;
    final int L = 100;
    IntStream.range(0, N).parallel().forEach(__-> {
      for (int j = 0; j < L; j++) {
        bitSet.flip(j);
      }
    });

    assertEquals(-1, bitSet.nextSetBit(0));
  }

  public void testStressCoarseGrainedBigSet() {
    final ConcurrentBitSet bitSet = ConcurrentBitSet.create();
    // must be even
    int N = Timings.adjustAccordingToMySpeed(1_000, true) / 2 * 2;
    final int L = 100_000;

    IntStream.range(0,N).parallel().forEach(__-> {
      for (int j = 0; j < L; j++) {
        bitSet.flip(j);
      }
    });

    assertEquals(-1, bitSet.nextSetBit(0));
  }

  public void testReadPerformance() {
    int len = 100_000;
    ConcurrentBitSet set = ConcurrentBitSet.create();
    Random random = new Random();
    int s = 0;
    for (int i = 0; i < len; i++) {
      set.set(i, random.nextBoolean());
      s += set.get(i) ? 1 : 0;
    }
    int sum = s;

    int N = 10_000;

    PlatformTestUtil.startPerformanceTest("ConcurrentBitSet.get() must be fast", 30_000, ()-> {
      int r = 0;
      for (int n = 0; n < N; n++) {
        for (int j = 0; j < len; j++) {
          r += set.get(j) ? 1 : 0;
        }
      }
      assertEquals(N * sum, r);
    }).assertTiming();
  }

  public void testParallelReadPerformance() {
    int len = 100000;
    ConcurrentBitSet set = ConcurrentBitSet.create();
    Random random = new Random();
    int s = 0;
    for (int i = 0; i < len; i++) {
      set.set(i, random.nextBoolean());
      s += set.get(i)?1:0;
    }
    int sum = s;

    int N = 10000;

    for (int i=0; i<10; i++) {
      long el = TimeoutUtil.measureExecutionTime(() ->
        IntStream.range(0,N).parallel().forEach(__-> {
          int r = 0;
          for (int j = 0; j < len; j++) {
            r += set.get(j)?1:0;
          }
          assertEquals(sum, r);
        })
      );

      LOG.debug("elapsed = " + el);
    }
  }

  public void testFlipPerformance() {
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
            r += set.flip(j)?1:0;
          }
        }
        assertEquals(N/2 * len, r);
      });

      LOG.debug("elapsed = " + el);
    }
  }

}
