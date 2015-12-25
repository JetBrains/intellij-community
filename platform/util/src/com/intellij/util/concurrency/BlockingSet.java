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
package com.intellij.util.concurrency;

import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Naive implementation of blocking set: class that allows {@link #put(Object)} lock by the {@code key} specified and
 * {@link #remove(Object)} it. The main feature is that another invocation of {@link #put(Object)} with the same {@code key} could not
 * be proceeded while one won't call {@link #remove(Object)} with this {@code key}.
 *
 * @author Alexander Koshevoy
 */
public class BlockingSet<T> {
  private final Set<T> set;

  private final Condition unlock;
  private final Lock lock;

  public BlockingSet() {
    set = new HashSet<T>();
    lock = new ReentrantLock();
    unlock = lock.newCondition();
  }

  /**
   * Acquires lock by {@code key}. If lock by {@code key} has been already acquired wait until it is released. Acquire is <b>not</b> reentrant.
   */
  public void put(@NotNull T key) {
    lock.lock();
    try {
      while (set.contains(key)) {
        unlock.awaitUninterruptibly();
      }
      set.add(key);
    }
    finally {
      lock.unlock();
    }
  }

  /**
   * Releases lock by {@code key}. If lock has not been acquired throws {@link IllegalStateException}.
   *
   * @throws IllegalStateException if lock by {@code key} has not been acquired.
   */
  public void remove(@NotNull T key) throws IllegalStateException {
    lock.lock();
    try {
      if (!set.contains(key)) {
        throw new IllegalStateException();
      }
      set.remove(key);
      unlock.signalAll();
    }
    finally {
      lock.unlock();
    }
  }

  public static <T> BlockingSet<T> newInstance() {
    return new BlockingSet<T>();
  }
}
