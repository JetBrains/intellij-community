// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev;

import org.jetbrains.annotations.ApiStatus;

import java.util.concurrent.locks.StampedLock;

@ApiStatus.Internal
public class FileRecordLock {
  public static final int SEGMENTS_COUNT = 16;
  public static final int SEGMENTS_MASK = 0b1111;

  private final StampedLock[] segmentedLock = new StampedLock[SEGMENTS_COUNT];

  public FileRecordLock() {
    for (int i = 0; i < SEGMENTS_COUNT; i++) {
      segmentedLock[i] = new StampedLock();
    }
  }

  public long lockForWrite(int fileId) {
    StampedLock lock = segmentedLock[fileId & SEGMENTS_MASK];
    return lock.writeLock();
  }

  public void unlockForWrite(int fileId, long stamp) {
    StampedLock lock = segmentedLock[fileId & SEGMENTS_MASK];
    lock.unlockWrite(stamp);
  }

  public long lockForRead(int fileId) {
    StampedLock lock = segmentedLock[fileId & SEGMENTS_MASK];
    return lock.readLock();
  }

  public long tryLockOptimisticForRead(int fileId) {
    StampedLock lock = segmentedLock[fileId & SEGMENTS_MASK];
    return lock.tryOptimisticRead();
  }

  public void unlockForRead(int fileId, long stamp) {
    StampedLock lock = segmentedLock[fileId & SEGMENTS_MASK];
    lock.unlockRead(stamp);
  }

}
