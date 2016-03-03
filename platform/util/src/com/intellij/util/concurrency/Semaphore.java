/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.progress.ProcessCanceledException;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

public class Semaphore {
  /**
   * Creates Semaphore in an up state
   */
  public Semaphore() { }

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
}