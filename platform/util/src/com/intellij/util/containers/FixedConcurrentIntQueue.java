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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * A queue holding {@code int} values which has fixed capacity.
 * When a value is pushed (via {@link #push} method) and the queue overflows (i.e. the size becomes greater than capacity),
 * the LRU value (i.e. the value which was pushed a {@code capacity} pushes ago) is removed from the queue and returned as a method result.
 * Thread safe.
 */
public class FixedConcurrentIntQueue {
  private final AtomicInteger tail = new AtomicInteger();
  final AtomicIntegerArray queue;
  private final int capacity;
  private final int tombValue;

  /**
   * @param capacity which restricts the queue size
   * @param tombValue the value which is guaranteed to not be used in the queue.
   */
  public FixedConcurrentIntQueue(int capacity, int tombValue) {
    this.capacity = capacity;
    this.tombValue = tombValue;
    queue = new AtomicIntegerArray(capacity);
    for (int i = 0; i < capacity; i++) {
      queue.set(i, tombValue);
    }
  }

  /**
   * @param value to be pushed in the queue
   * @return value which was evicted off the queue because of the overflow or {@link #tombValue} if no overflow happened
   */
  public int push(int value) {
    if (value == tombValue) {
      throw new IllegalArgumentException("Must not use tomb value: "+value);
    }
    int index = getAndIncrement();
    return queue.getAndSet(index, value);
  }

  private int getAndIncrement() {
    int index;
    int next;
    do {
      index = tail.get();
      next = index + 1;
      if (next >= capacity) {
        next -= capacity;
      }
    } while (!tail.compareAndSet(index, next));
    return index;
  }
}