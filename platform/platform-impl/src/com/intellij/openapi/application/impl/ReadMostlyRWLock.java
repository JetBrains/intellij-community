/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.application.impl;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.containers.ConcurrentList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

/**
 * Read-Write lock optimised for mostly reads.
 * Scales better than {@link java.util.concurrent.locks.ReentrantReadWriteLock} with a number of readers due to reduced contention thanks to thread local structures.
 * The lock has writer preference, i.e. no reader can obtain read lock while there is a writer pending.
 * NOT reentrant.
 * Writer assumed to issue write requests from the dedicated thread {@link #writeThread} only.
 * Readers must not issue read requests from the write thread {@link #writeThread}.
 * <br>
 * Based on paper <a href="http://mcg.cs.tau.ac.il/papers/ppopp2013-rwlocks.pdf">"NUMA-Aware Reader-Writer Locks" by Calciu, Dice, Lev, Luchangco, Marathe, Shavit.</a><br>
 * The elevator pitch explanation of the algorithm:<br>
 * Read lock: flips {@link Reader#readRequested} bit in its own thread local {@link Reader} structure and waits for writer to release its lock by checking {@link #writeRequested}.<br>
 * Write lock: sets global {@link #writeRequested} bit and waits for all readers (in global {@link #readers} list) to release their locks by checking {@link Reader#readRequested} for all readers.
 */
class ReadMostlyRWLock {
  private final Thread writeThread;
  private volatile boolean writeRequested;  // this writer is requesting or obtained the write access
  private volatile boolean writeAcquired;   // this writer obtained the write lock
  // All reader threads are registered here. Dead readers are garbage collected in writeUnlock().
  private final ConcurrentList<Reader> readers = ContainerUtil.createConcurrentList();

  ReadMostlyRWLock(@NotNull Thread writeThread) {
    this.writeThread = writeThread;
  }

  // Each reader thread has instance of this struct in its thread local. it's also added to global "readers" list.
  private static class Reader {
    @NotNull private final Thread thread;   // its thread
    private volatile boolean readRequested; // this reader is requesting or obtained read access. Written by reader thread only, read by writer.
    private volatile boolean blocked;       // this reader is blocked waiting for the writer thread to release write lock. Written by reader thread only, read by writer.

    Reader(@NotNull Thread readerThread) {
      thread = readerThread;
    }
  }

  private final ThreadLocal<Reader> R = new ThreadLocal<Reader>(){
    @Override
    protected Reader initialValue() {
      Reader status = new Reader(Thread.currentThread());
      boolean added = readers.addIfAbsent(status);
      assert added : readers + "; "+Thread.currentThread();
      return status;
    }
  };

  boolean isWriteThread() {
    return Thread.currentThread() == writeThread;
  }

  boolean isReadLockedByThisThread() {
    checkReadThreadAccess();
    Reader status = R.get();
    return status.readRequested;
  }

  void readLock() {
    checkReadThreadAccess();
    Reader status = R.get();

    // be optimistic
    if (tryReadLock(status)) {
      return;
    }

    for(int iter=0;;iter++) {
      if (tryReadLock(status)) {
        return;
      }

      ProgressManager.checkCanceled();

      if (iter > SPIN_TO_WAIT_FOR_LOCK) {
        status.blocked = true;
        try {
          LockSupport.parkNanos(this, 1000000);  // unparked by writeUnlock
        }
        finally {
          status.blocked = false;
        }
      }
      else {
        Thread.yield();
      }
    }
  }

  void readUnlock() {
    checkReadThreadAccess();
    Reader status = R.get();
    status.readRequested = false;
    if (writeRequested) {
      LockSupport.unpark(writeThread);  // parked by writeLock()
    }
  }

  boolean tryReadLock() {
    checkReadThreadAccess();
    Reader status = R.get();
    return tryReadLock(status);
  }

  private boolean tryReadLock(Reader status) {
    if (!writeRequested) {
      status.readRequested = true;
      if (!writeRequested) {
        return true;
      }
      status.readRequested = false;
    }
    return false;
  }

  private static final int SPIN_TO_WAIT_FOR_LOCK = 100;
  void writeLock() {
    checkWriteThreadAccess();
    assert !writeRequested;
    assert !writeAcquired;

    writeRequested = true;
    for (int iter=0; ;iter++) {
      if (areAllReadersIdle()) {
        writeAcquired = true;
        break;
      }

      if (iter > SPIN_TO_WAIT_FOR_LOCK) {
        LockSupport.parkNanos(this, 1000000);  // unparked by readUnlock
      }
      else {
        Thread.yield();
      }
    }
  }

  void writeUnlock() {
    checkWriteThreadAccess();
    writeAcquired = false;
    writeRequested = false;
    List<Reader> dead = new ArrayList<>(readers.size());
    for (Reader reader : readers) {
      if (reader.blocked) {
        LockSupport.unpark(reader.thread); // parked by readLock()
      }
      else if (!reader.thread.isAlive()) {
        dead.add(reader);
      }
    }
    readers.removeAll(dead);
  }

  private void checkWriteThreadAccess() {
    if (Thread.currentThread() != writeThread) {
      throw new IllegalStateException("Current thread: "+Thread.currentThread()+"; expected: "+ writeThread);
    }
  }

  private void checkReadThreadAccess() {
    if (Thread.currentThread() == writeThread) {
      throw new IllegalStateException("Must not start read from the write thread: "+Thread.currentThread());
    }
  }

  boolean tryWriteLock() {
    checkWriteThreadAccess();
    assert !writeRequested;
    assert !writeAcquired;

    writeRequested = true;
    if (areAllReadersIdle()) {
      writeAcquired = true;
      return true;
    }

    writeRequested = false;
    return false;
  }

  private boolean areAllReadersIdle() {
    for (Reader reader : readers) {
      if (reader.readRequested) {
        return false;
      }
    }

    return true;
  }

  boolean isWriteLocked() {
    return writeAcquired;
  }
}
