// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency;

import com.intellij.openapi.application.AccessToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * Intended to use within try-with-resources.
 */
public final class LockToken extends AccessToken {

  private final Lock myLock;

  private LockToken(Lock lock) {
    myLock = lock;
  }

  @Override
  public void finish() {
    myLock.unlock();
  }

  @NotNull
  public static LockToken acquireLock(@NotNull Lock lock) {
    lock.lock();
    return new LockToken(lock);
  }

  @Nullable
  public static LockToken attemptLock(@NotNull Lock lock, long time) throws InterruptedException {
    if (lock.tryLock(time, TimeUnit.MILLISECONDS)) {
      return new LockToken(lock);
    }
    else {
      return null;
    }
  }
}
