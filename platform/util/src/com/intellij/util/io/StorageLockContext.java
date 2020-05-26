// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.locks.ReentrantReadWriteLock;

@ApiStatus.Internal
public final class StorageLockContext {
  private final boolean myCheckThreadAccess;
  @NotNull
  private final ReentrantReadWriteLock myLock;
  @NotNull
  private final StorageLock myStorageLock;
  private final boolean myUseReadWriteLock;

  public StorageLockContext(@NotNull StorageLock lock, boolean checkAccess, boolean useReadWriteLock) {
    myLock = new ReentrantReadWriteLock();
    myStorageLock = lock;
    myCheckThreadAccess = checkAccess;
    myUseReadWriteLock = useReadWriteLock;
  }

  public StorageLockContext(boolean checkAccess, boolean useReadWriteLock) {
    this(PagedFileStorage.ourLock, checkAccess, useReadWriteLock);
  }

  public StorageLockContext(boolean checkAccess) {
    this(PagedFileStorage.ourLock, checkAccess, false);
  }

  public void lockRead() {
    if (myUseReadWriteLock) {
      myLock.readLock().lock();
    }
    else {
      myLock.writeLock().lock();
    }
  }

  public void unlockRead() {
    if (myUseReadWriteLock) {
      myLock.readLock().unlock();
    }
    else {
      myLock.writeLock().unlock();
    }
  }

  public void lockWrite() {
    myLock.writeLock().lock();
  }
  public void unlockWrite() {
    myLock.writeLock().unlock();
  }

  @ApiStatus.Internal
  @NotNull
  StorageLock getStorageLock() {
    return myStorageLock;
  }

  @ApiStatus.Internal
  void checkThreadAccess(boolean read) {
    if (myCheckThreadAccess) {
      if (read) {
        if (myLock.getReadHoldCount() > 0 || myLock.writeLock().isHeldByCurrentThread()) return;
        throw new IllegalStateException("Must hold StorageLock read lock to access PagedFileStorage");
      }
      else {
        if (myLock.writeLock().isHeldByCurrentThread()) return;
        throw new IllegalStateException("Must hold StorageLock write lock to access PagedFileStorage");
      }
    }
  }
}
