// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency;

import com.intellij.openapi.progress.ProcessCanceledException;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

public class Semaphore {
  /**
   * Creates Semaphore in an up state
   */
  public Semaphore() { }

  /**
   * Creates a semaphore and immediately puts it down the specified number of times
   */
  public Semaphore(int downs) {
    if (downs < 0) {
      throw new IllegalArgumentException("A non-negative amount of 'downs' expected, found " + downs);
    }
    for (int i = 0; i < downs; i++) {
      down();
    }
  }

  private static final class Sync extends AbstractQueuedSynchronizer {
    @Override
    public int tryAcquireShared(int acquires) {
      return getState() == 0 ? 1 : -1;
    }

    @Override
    public boolean tryReleaseShared(int releases) {
      // Decrement count; signal when transition to zero
      while (true) {
        int c = getState();
        if (c == 0) return false;
        int next = c - 1;
        if (compareAndSetState(c, next)) return next == 0;
      }
    }

    private void down() {
      while (true) {
        int current = getState();
        int next = current + 1;
        if (compareAndSetState(current, next)) return;
      }
    }

    private boolean isUp() {
      return getState() == 0;
    }
  }

  private final Sync sync = new Sync();

  public void up() {
    tryUp();
  }

  public boolean tryUp() {
    return sync.releaseShared(1);
  }

  public void down() {
    sync.down();
  }

  public void waitFor() {
    try {
      waitForUnsafe();
    }
    catch (InterruptedException e) {
      throw new ProcessCanceledException(e);
    }
  }

  public void waitForUnsafe() throws InterruptedException {
    sync.acquireSharedInterruptibly(1);
  }

  // true if semaphore became free
  public boolean waitFor(final long msTimeout) {
    try {
      return waitForUnsafe(msTimeout);
    }
    catch (InterruptedException e) {
      throw new ProcessCanceledException(e);
    }
  }

  // true if semaphore became free
  public boolean waitForUnsafe(long msTimeout) throws InterruptedException {
    if (sync.tryAcquireShared(1) >= 0) return true;
    return sync.tryAcquireSharedNanos(1, TimeUnit.MILLISECONDS.toNanos(msTimeout));
  }

  public boolean isUp() {
    return sync.isUp();
  }
}