// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.StampedLock;

/**
 * Lock used to protect file-record accesses in {@link FSRecordsImpl}
 * <p>
 * Basically, it is a segmented read-write lock ({@link StampedLock}), with an additional 'lock for hierarchy update'
 * locking mode ({@link #lockForHierarchyUpdate(int)}).
 * This is not a generally applicable lock by any means: it is very much tailored for the specific needs of {@link FSRecordsImpl}.
 * <p>
 * The lock is <b>NOT reentrant</b> (because {@link StampedLock} is not reentrant), and an attempt to lock already locked fileId
 * down the stack <b>leads to deadlock</b> -- so one needs to be quite careful to use this lock.
 * 'Lock for hierarchy update' mode is also not reentrant.
 *
 * @see StampedLock
 */
class FileRecordLock {
  private static final int SEGMENTS_COUNT = 16;
  private static final int SEGMENTS_MASK = 0b1111;

  private final Segment[] segments = new Segment[SEGMENTS_COUNT];

  {
    for (int i = 0; i < SEGMENTS_COUNT; i++) {
      segments[i] = new Segment();
    }
  }

  public long lockForWrite(int fileId) {
    StampedLock lock = segmentFor(fileId);
    return lock.writeLock();
  }

  public void unlockForWrite(int fileId,
                             long stamp) {
    StampedLock lock = segmentFor(fileId);
    lock.unlockWrite(stamp);
  }

  public long lockForRead(int fileId) {
    StampedLock lock = segmentFor(fileId);
    return lock.readLock();
  }

  public void unlockForRead(int fileId, long stamp) {
    StampedLock lock = segmentFor(fileId);
    lock.unlockRead(stamp);
  }

  public StampedLock lockFor(int fileId) {
    return segmentFor(fileId);
  }

  /**
   * Locks fileId for "hierarchy update": any attempt to lock same fileId for hierarchy update will be blocked until fileId is
   * released with {@link #unlockForHierarchyUpdate(int)} call.
   * <p>
   * 'Hierarchy update' locking mode is independent of regular read/write locking: i.e. fileId locked for hierarchy update is not
   * locked for read or write, and could be locked for read/write independently.
   * <p>
   * Hierarchy update lock is NOT reentrant: an attempt to lock the same fileId for hierarchy update down the stack in the same
   * thread lead to deadlock.
   */
  public void lockForHierarchyUpdate(int fileId) {
    segmentFor(fileId).lockHierarchy(fileId);
  }

  public void unlockForHierarchyUpdate(int fileId) {
    segmentFor(fileId).unlockHierarchy(fileId);
  }


  private Segment segmentFor(int fileId) {
    return segments[fileId & SEGMENTS_MASK];
  }

  private static class Segment extends StampedLock {

    /** Set of fileId for which hierarchy updates are now ongoing, so those id are 'locked' for hierarchy updates now */
    private final IntSet hierarchyUpdatesInProcess = new IntOpenHashSet();

    public void lockHierarchy(int id) {
      for (int turn = 0; ; turn++) {
        long lockStamp = writeLock();
        try {
          if (!hierarchyUpdatesInProcess.contains(id)) {
            hierarchyUpdatesInProcess.add(id);
            return;
          }
        }
        finally {
          unlockWrite(lockStamp);
        }

        //use active spinning, since stamped lock doesn't support Condition to await()/signal() on:
        if (turn < 64) {
          Thread.onSpinWait();
        }
        else {
          LockSupport.parkNanos(1000);
        }
      }
    }

    public void unlockHierarchy(int id) {
      long lockStamp = writeLock();
      try {
        boolean actuallyRemoved = hierarchyUpdatesInProcess.remove(id);
        if (!actuallyRemoved) {
          throw new IllegalStateException("Trying to unlock(" + id + ") which is not currently locked " + hierarchyUpdatesInProcess);
        }
      }
      finally {
        unlockWrite(lockStamp);
      }
    }
  }
}
