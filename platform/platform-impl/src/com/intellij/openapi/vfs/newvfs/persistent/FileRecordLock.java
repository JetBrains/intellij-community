// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Lock for protecting access to file records (identified by integer fileId)
 * Attempt to lock ID that is already locked made the current thread waiting until that ID is unlocked.
 */
final class FileRecordLock {

  //Ideally, each fileId should have its own lock, but this is too expensive, so we use segmented lock

  //TODO RC: Currently we use FileRecordLock for updating file hierarchy -- which is relatively long process, since it
  //         involves children modification. Which is why 'ReentrantLock with fileId list per segment' approach was used --
  //         it allows to keep particular fileId locked for some time, without locking other fileIds, even those falling
  //         into the same segment.
  //         In bright future, we should use per-file-record locks for almost every file-record modification involving >1 field.
  //         This is _required_ for correctness in multithreaded env -- we are currently able to bypass that requirement
  //         only because usually there are some locks acquired upper the stack, and VFS implicitly piggyback on that.
  //         Such per-file-record locks are most of the time short, and most of the time for-read. For that kind of locking
  //         segmented StampedLock seems much more suitable -- the contention is low by itself (because most locks are short)
  //         and segmentation makes it even lower, so keeping list of fileIds is not needed -- we could just lock the whole
  //         segment.

  private final SegmentLock[] segments;

  FileRecordLock() {
    this(16);
  }

  FileRecordLock(int segmentsCount) {
    segments = new SegmentLock[segmentsCount];
    for (int i = 0; i < segments.length; i++) {
      segments[i] = new SegmentLock();
    }
  }

  public void lock(int id) {
    int index = toIndex(id);
    segments[index].lock(id);
  }

  public void unlock(int id) {
    int index = toIndex(id);
    segments[index].unlock(id);
  }

  private int toIndex(int id) {
    return id % segments.length;
  }

  private static class SegmentLock {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition unlockCondition = lock.newCondition();
    private final IntSet lockedIds = new IntOpenHashSet();

    public void lock(int id) {
      lock.lock();
      try {
        while (lockedIds.contains(id)) {
          unlockCondition.awaitUninterruptibly();
        }
        lockedIds.add(id);
      }
      finally {
        lock.unlock();
      }
    }

    public void unlock(int id) {
      lock.lock();
      try {
        boolean actuallyRemoved = lockedIds.remove(id);
        if (actuallyRemoved) {
          //This wakes up all threads waiting -- i.e. lockedIds.size() -- we assume it is usually just a few of them.
          // But there could be pathological scenarios there a lot of threads waiting: and each thread will need to
          // re-acquire lock, check lockedIds.contains() -- and all threads but one return to waiting after the check.
          unlockCondition.signalAll();
        }
        else {
          throw new IllegalStateException("Trying to unlock(" + id + ") which is not currently locked " + lockedIds);
        }
      }
      finally {
        lock.unlock();
      }
    }
  }
}
