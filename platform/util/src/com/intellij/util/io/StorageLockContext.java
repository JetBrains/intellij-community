// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.locks.ReentrantReadWriteLock;

@ApiStatus.Internal
public final class StorageLockContext {
  private static final DirectBufferPool ourDefaultPool = new DirectBufferPool();

  private final boolean myCheckThreadAccess;
  @NotNull
  private final ReentrantReadWriteLock myLock;
  @NotNull
  private final DirectBufferPool myDirectBufferPool;
  private final boolean myUseReadWriteLock;

  public StorageLockContext(@NotNull DirectBufferPool lock,
                            boolean checkAccess,
                            boolean useReadWriteLock) {
    myLock = new ReentrantReadWriteLock();
    myDirectBufferPool = lock;
    myCheckThreadAccess = checkAccess;
    myUseReadWriteLock = useReadWriteLock;
  }

  public StorageLockContext(boolean checkAccess,
                            boolean useReadWriteLock) {
    this(ourDefaultPool, checkAccess, useReadWriteLock);
  }

  public StorageLockContext(boolean checkAccess) {
    this(ourDefaultPool, checkAccess, false);
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
  DirectBufferPool getBufferPool() {
    return myDirectBufferPool;
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
