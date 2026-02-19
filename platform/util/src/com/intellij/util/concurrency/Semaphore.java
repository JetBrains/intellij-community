// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.openapi.progress.ProcessCanceledException;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * A semaphore implementation throwing {@link ProcessCanceledException} instead of {@link InterruptedException}
 *
 * This implementation is suitable when you need wait for one or several events to occur.
 * Note that {@link #waitFor} does not acquire permit.<p>
 * The typical usage is:
 * <pre>
 * {@code
 *   Semaphore semaphore = new Semaphore();
 *   semaphore.down();
 *
 *   new Thread(() -> {
 *     doTheJob();        // the job is done here
 *     semaphore.up();
 *   }).start();
 *
 *   semaphore.waitFor(); // wait for the job to finish
 * }
 * </pre>
 *
 * Use {@link Semaphore#down} to remove a permit from the semaphore <p>
 * Use {@link Semaphore#up} to return a permit to the semaphore <p>
 * Use {@link Semaphore#waitFor} to wait until the semaphore gets at least one permit. waitFor blocks until there is one.
 * Note that it does not perform a ` down ` operation.
 */
public final class Semaphore {
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

  /**
   * @return true if semaphore became free
   */
  public boolean waitFor(final long msTimeout) {
    try {
      return waitForUnsafe(msTimeout);
    }
    catch (InterruptedException e) {
      throw new ProcessCanceledException(e);
    }
  }

  /**
   * @return true if semaphore became free
   */
  public boolean waitForUnsafe(long msTimeout) throws InterruptedException {
    if (sync.tryAcquireShared(1) >= 0) return true;
    return sync.tryAcquireSharedNanos(1, TimeUnit.MILLISECONDS.toNanos(msTimeout));
  }

  public boolean isUp() {
    return sync.isUp();
  }
}