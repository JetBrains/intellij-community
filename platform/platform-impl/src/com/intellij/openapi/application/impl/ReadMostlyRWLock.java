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

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.util.containers.ConcurrentList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.application.impl.ReadMostlyRWLock");
  private final Thread writeThread;
  volatile boolean writeRequested;  // this writer is requesting or obtained the write access
  private volatile boolean writeAcquired;   // this writer obtained the write lock
  // All reader threads are registered here. Dead readers are garbage collected in writeUnlock().
  private final ConcurrentList<Reader> readers = ContainerUtil.createConcurrentList();

  private final Map<Thread, SuspensionId> privilegedReaders = new ConcurrentHashMap<>();

  private volatile SuspensionId currentSuspension;

  ReadMostlyRWLock(@NotNull Thread writeThread) {
    this.writeThread = writeThread;
  }

  // Each reader thread has instance of this struct in its thread local. it's also added to global "readers" list.
  private static class Reader {
    @NotNull private final Thread thread;   // its thread
    private volatile boolean readRequested; // this reader is requesting or obtained read access. Written by reader thread only, read by writer.
    private volatile boolean blocked;       // this reader is blocked waiting for the writer thread to release write lock. Written by reader thread only, read by writer.
    private boolean impatientReads; // true if should throw PCE on contented read lock
    Reader(@NotNull Thread readerThread) {
      thread = readerThread;
    }
  }

  private final ThreadLocal<Reader> R = ThreadLocal.withInitial(() -> {
    Reader status = new Reader(Thread.currentThread());
    boolean added = readers.addIfAbsent(status);
    assert added : readers + "; "+Thread.currentThread();
    return status;
  });

  boolean isWriteThread() {
    return Thread.currentThread() == writeThread;
  }

  boolean isReadLockedByThisThread() {
    checkReadThreadAccess();
    Reader status = R.get();
    throwIfImpatient(status);
    return status.readRequested;
  }

  void readLock() {
    checkReadThreadAccess();
    Reader status = R.get();

    for (int iter = 0; ; iter++) {
      if (tryReadLock(status, true)) {
        return;
      }

      ProgressManager.checkCanceled();
      waitABit(status, iter);
    }
  }

  private void waitABit(Reader status, int iteration) {
    if (iteration > SPIN_TO_WAIT_FOR_LOCK) {
      status.blocked = true;
      try {
        throwIfImpatient(status);
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

  private void throwIfImpatient(Reader status) {
    // when client explicitly runs in non-cancelable block do not throw from within nested read actions
    if (status.impatientReads && writeRequested && !ProgressManager.getInstance().isInNonCancelableSection() && CoreProgressManager.ENABLED) {
      throw new ApplicationUtil.CannotRunReadActionException();
    }
  }

  /**
   * Executes a {@code runnable} in an "impatient" mode.
   * In this mode any attempt to grab read lock
   * will fail (i.e. throw {@link ApplicationUtil.CannotRunReadActionException})
   * if there is a pending write lock request.
   */
  void executeByImpatientReader(@NotNull Runnable runnable) throws ApplicationUtil.CannotRunReadActionException {
    checkReadThreadAccess();
    Reader status = R.get();
    boolean old = status.impatientReads;
    try {
      status.impatientReads = true;
      runnable.run();
    }
    finally {
      status.impatientReads = old;
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
    return tryReadLock(status, true);
  }

  private boolean tryReadLock(Reader status, boolean checkPrivileges) {
    if (!writeRequested) {
      if (checkPrivileges && currentSuspension != null && !privilegedReaders.containsKey(Thread.currentThread())) {
        return false;
      }
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

  AccessToken writeSuspend() {
    SuspensionId prevSuspension = currentSuspension;
    if (prevSuspension == null) {
      currentSuspension = new SuspensionId();
    }
    writeUnlock();
    return new AccessToken() {
      @Override
      public void finish() {
        writeLock();
        currentSuspension = prevSuspension;
        if (prevSuspension == null) {
          ensureNoPrivilegedReaders();
        }
      }
    };
  }

  private void ensureNoPrivilegedReaders() {
    if (!privilegedReaders.isEmpty()) {
      List<String> offenderNames = ContainerUtil.map(privilegedReaders.keySet(), Thread::getName);
      privilegedReaders.clear();
      LOG.error("Pooled threads created during write action suspension should have been terminated: " + offenderNames,
                new Attachment("threadDump.txt", ThreadDumper.dumpThreadsToString()));
    }
  }

  @Nullable
  SuspensionId currentReadPrivilege() {
    return privilegedReaders.get(Thread.currentThread());
  }

  @NotNull AccessToken applyReadPrivilege(@Nullable SuspensionId context) {
    Reader status = R.get();
    int iter = 0;
    while (context != null && context == currentSuspension) {
      if (tryReadLock(status, false)) {
        try {
          return context == currentSuspension ? grantReadPrivilege() : AccessToken.EMPTY_ACCESS_TOKEN;
        }
        finally {
          readUnlock();
        }
      }

      waitABit(status, iter++);
    }
    return AccessToken.EMPTY_ACCESS_TOKEN;
  }

  @NotNull
  AccessToken grantReadPrivilege() {
    Thread thread = Thread.currentThread();
    privilegedReaders.put(thread, currentSuspension);
    return new AccessToken() {
      @Override
      public void finish() {
        privilegedReaders.remove(thread);
      }
    };
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

  static class SuspensionId {}
}
