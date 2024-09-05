// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import org.jetbrains.annotations.ApiStatus;

import java.util.concurrent.locks.StampedLock;

class FileRecordLock {
  private static final int SEGMENTS_COUNT = 16;
  private static final int SEGMENTS_MASK = 0b1111;

  private final StampedLock[] segmentedLock = new StampedLock[SEGMENTS_COUNT];

  {
    for (int i = 0; i < SEGMENTS_COUNT; i++) {
      segmentedLock[i] = new StampedLock();
    }
  }

  public long lockForWrite(int fileId) {
    StampedLock lock = lockFor(fileId);
    return lock.writeLock();
  }

  public void unlockForWrite(int fileId, long stamp) {
    StampedLock lock = lockFor(fileId);
    lock.unlockWrite(stamp);
  }

  public long lockForRead(int fileId) {
    StampedLock lock = lockFor(fileId);
    return lock.readLock();
  }

  public void unlockForRead(int fileId, long stamp) {
    StampedLock lock = lockFor(fileId);
    lock.unlockRead(stamp);
  }

  public StampedLock lockFor(int fileId){
    return segmentedLock[fileId & SEGMENTS_MASK];
  }
}
