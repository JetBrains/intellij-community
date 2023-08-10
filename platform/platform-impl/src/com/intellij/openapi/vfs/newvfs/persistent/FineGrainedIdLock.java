// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Maintains set of integer IDs 'locked' at the moment. Attempt to lock ID that is already locked
 * forces current thread to wait until that ID will be unlocked.
 *
 * RC: I think 'fine-grained' is a bit misleading here, since usually this term indicates use of multiple
 *     locks to avoid single point of contention. But this is not true here, single point of contention
 *     does exist: many threads trying to lock/unlock different IDs will contend on a single shared lock.
 */
final class FineGrainedIdLock {
  private final IntSet lockedIds = new IntOpenHashSet(Runtime.getRuntime().availableProcessors());
  private final Lock lock = new ReentrantLock();
  private final Condition unlockCondition = lock.newCondition();

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
      lockedIds.remove(id);
      //RC: this wakes up all threads waiting -- i.e. myLockedIds.size(), which could be a lot.
      //    And each thread will need to re-acquire myLock, to check lockedIds.contains() -- and
      //    all threads but one return to waiting after the check. Could be quite ineffective
      //    if there are a lot of IDs locked.
      unlockCondition.signalAll();
    }
    finally {
      lock.unlock();
    }
  }
}
