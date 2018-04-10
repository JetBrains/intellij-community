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

import com.intellij.testFramework.Timings;
import com.intellij.util.ConcurrencyUtil;
import junit.framework.TestCase;

public class ConcurrentBitSetTest extends TestCase {
  public void test() {
    ConcurrentBitSet bitSet = new ConcurrentBitSet();
    final ConcurrentBitSet emptySet = new ConcurrentBitSet();
    int N = 3000;
    assertEquals(0, bitSet.nextClearBit(0));
    assertEquals(bitSet, emptySet);
    for (int i=0; i<N;i++) {
      assertEquals(-1, bitSet.nextSetBit(i));
      assertEquals(i, bitSet.nextClearBit(i));
      assertFalse(bitSet.get(i));
      bitSet.set(i);
      assertTrue(bitSet.get(i));
      bitSet.clear(i);
      assertFalse(bitSet.get(i));
      assertEquals(bitSet, emptySet);
    }
    bitSet = new ConcurrentBitSet();
    for (int b=0;b<N;b++) {
      assertEquals(bitSet, emptySet);
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
    assertFalse(bitSet.equals(emptySet));
    bitSet.clear();
    assertEquals(bitSet, emptySet);
  }

  public void testStress() {
    final ConcurrentBitSet bitSet = new ConcurrentBitSet();
    int N = Timings.adjustAccordingToMySpeed(100, true);
    final int L = 100;

    Thread[] threads = new Thread[N];
    for (int i=0; i<N;i++) {
      Thread thread = new Thread(() -> {
        for (int i1 = 0; i1 < 10_000; i1++) {
          for (int j = 0; j < L; j++) {
            bitSet.flip(j);
          }
        }
      }, "conc bit set");
      threads[i] = thread;
      thread.start();
    }
    ConcurrencyUtil.joinAll(threads);

    assertEquals(-1, bitSet.nextSetBit(0));
  }
  public void testStress2_Performance() {
    final ConcurrentBitSet bitSet = new ConcurrentBitSet();
    int N = Timings.adjustAccordingToMySpeed(10, true);
    final int L = 1_000_000;

    Thread[] threads = new Thread[N];
    for (int i=0; i<N;i++) {
      Thread thread = new Thread(() -> {
        for (int i1 = 0; i1 < 100; i1++) {
          for (int j = 0; j < L; j++) {
            bitSet.flip(j);
          }
        }
      }, "conc bit stress");
      threads[i] = thread;
      thread.start();
    }
    ConcurrencyUtil.joinAll(threads);

    assertEquals(-1, bitSet.nextSetBit(0));
  }
}
