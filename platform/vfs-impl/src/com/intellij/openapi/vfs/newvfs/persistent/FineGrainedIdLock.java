// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

final class FineGrainedIdLock {
  private final IntSet myLockedIds = new IntOpenHashSet(Runtime.getRuntime().availableProcessors());
  private final Lock myLock = new ReentrantLock();
  private final Condition myUnlockCondition = myLock.newCondition();

  public void lock(int id) {
    myLock.lock();
    try {
      while (myLockedIds.contains(id)) {
        myUnlockCondition.awaitUninterruptibly();
      }
      myLockedIds.add(id);
    }
    finally {
      myLock.unlock();
    }
  }

  public void unlock(int id) {
    myLock.lock();
    try {
      myLockedIds.remove(id);
      myUnlockCondition.signalAll();
    }
    finally {
      myLock.unlock();
    }
  }
}
