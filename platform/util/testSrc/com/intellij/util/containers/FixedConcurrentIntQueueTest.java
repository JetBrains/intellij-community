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

import gnu.trove.TIntArrayList;
import junit.framework.TestCase;

import java.util.Set;

public class FixedConcurrentIntQueueTest extends TestCase {
  public void testSimple() {
    int tombValue = -1;
    final FixedConcurrentIntQueue queue = new FixedConcurrentIntQueue(4, tombValue);
    assertEquals(tombValue, queue.push(2));
    assertEquals(tombValue, queue.push(3));
    assertEquals(tombValue, queue.push(4));
    assertEquals(tombValue, queue.push(5));
    assertEquals(2, queue.push(6));
    assertEquals(3, queue.push(7));
    assertEquals(4, queue.push(8));
    assertEquals(5, queue.push(9));
    assertEquals(6, queue.push(0));
  }

  public void testIntegerOverflow() {
    int tombValue = -1;
    final FixedConcurrentIntQueue queue = new FixedConcurrentIntQueue(4, tombValue);
    for (int i=10; i!=9; i++) {
      queue.push(2); // must not AIOOBE
    }
  }

  public void testStress() throws InterruptedException {
    for (int k=0; k<100; k++) {
      final int N = 1 << 20;
      final FixedConcurrentIntQueue queue = new FixedConcurrentIntQueue(N, -1);
      final Thread[] threads = new Thread[8];
      final Set<Integer> result = new ConcurrentHashSet<Integer>();
      for (int i = 0; i < threads.length; i++) {
        final int finalI = i;
        Thread thread = new Thread(new Runnable() {
          @Override
          public void run() {
            TIntArrayList evicted = new TIntArrayList();
            for (int i = N / threads.length * finalI; i < N / threads.length * finalI + N / threads.length; i++) {
              int f = queue.push(i);
              evicted.add(f);
            }
            for (int f : evicted.toNativeArray()) {
              result.add(f);
            }
          }
        },i + "");
        threads[i] = thread;
        thread.start();
      }

      for (Thread thread : threads) {
        thread.join();
      }

      int size = result.size();
      assertEquals(1, size);
      result.clear();
      for (int i=0; i<N;i++) {
        result.add(queue.queue.get(i));
      }
      assertEquals(N, result.size());
    }
  }
}
