// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency;

import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
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
    set = new HashSet<>();
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
    return new BlockingSet<>();
  }
}
