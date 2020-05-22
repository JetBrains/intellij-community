// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import org.jetbrains.annotations.ApiStatus;

import java.util.concurrent.locks.ReentrantLock;

public final class StorageLockContext {
  private final boolean myCheckThreadAccess;
  private final ReentrantLock myLock;
  private final StorageLock myStorageLock;

  StorageLockContext(StorageLock lock, boolean checkAccess) {
    myLock = new ReentrantLock();
    myStorageLock = lock;
    myCheckThreadAccess = checkAccess;
  }

  public StorageLockContext(boolean checkAccess) {
    this(PagedFileStorage.ourLock, checkAccess);
  }

  public void lock() {
    myLock.lock();
  }
  public void unlock() {
    myLock.unlock();
  }

  @ApiStatus.Internal
  StorageLock getStorageLock() {
    return myStorageLock;
  }

  @ApiStatus.Internal
  void checkThreadAccess() {
    if (myCheckThreadAccess && !myLock.isHeldByCurrentThread()) {
      throw new IllegalStateException("Must hold StorageLock lock to access PagedFileStorage");
    }
  }
}
