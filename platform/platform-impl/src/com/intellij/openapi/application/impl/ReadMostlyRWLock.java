// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.containers.ConcurrentList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import static com.intellij.openapi.progress.util.ProgressIndicatorUtils.cancelActionsToBeCancelledBeforeWrite;

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
final class ReadMostlyRWLock {
  @NotNull final Thread writeThread;
  @VisibleForTesting
  volatile boolean writeRequested;  // this writer is requesting or obtained the write access
  private final AtomicBoolean writeIntent = new AtomicBoolean(false);
  private volatile boolean writeAcquired;   // this writer obtained the write lock
  // All reader threads are registered here. Dead readers are garbage collected in writeUnlock().
  private final ConcurrentList<Reader> readers = ContainerUtil.createConcurrentList();

  private volatile boolean writeSuspended;
  // time stamp (nanoTime) of the last check for dead reader threads in writeUnlock().
  // (we have to reduce frequency of this "dead readers GC" activity because Thread.isAlive() turned out to be too expensive)
  private volatile long deadReadersGCStamp;

  ReadMostlyRWLock(@NotNull Thread writeThread) {
    this.writeThread = writeThread;
  }

  // Each reader thread has instance of this struct in its thread local. it's also added to global "readers" list.
  static class Reader {
    @NotNull private final Thread thread;   // its thread
    volatile boolean readRequested; // this reader is requesting or obtained read access. Written by reader thread only, read by writer.
    private volatile boolean blocked;       // this reader is blocked waiting for the writer thread to release write lock. Written by reader thread only, read by writer.
    private boolean impatientReads; // true if should throw PCE on contented read lock
    Reader(@NotNull Thread readerThread) {
      thread = readerThread;
    }

    @Override
    @NonNls
    public String toString() {
      return "Reader{" +
             "thread=" + thread +
             ", readRequested=" + readRequested +
             ", blocked=" + blocked +
             ", impatientReads=" + impatientReads +
             '}';
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
    return status.readRequested;
  }

  // null means lock already acquired, Reader means lock acquired successfully
  Reader startRead() {
    if (Thread.currentThread() == writeThread) return null;
    Reader status = R.get();
    throwIfImpatient(status);
    if (status.readRequested) return null;

    if (!tryReadLock(status)) {
      ProgressIndicator progress = ProgressIndicatorProvider.getGlobalProgressIndicator();
      for (int iter = 0; ; iter++) {
        if (tryReadLock(status)) {
          break;
        }
        // do not run any checkCanceled hooks to avoid deadlock
        if (progress != null && progress.isCanceled() && !ProgressManager.getInstance().isInNonCancelableSection()) {
          throw new ProcessCanceledException();
        }
        waitABit(status, iter);
      }
    }
    return status;
  }

  // return tristate: null means lock already acquired, Reader with readRequested==true means lock was successfully acquired, Reader with readRequested==false means lock was not acquired
  Reader startTryRead() {
    if (Thread.currentThread() == writeThread) return null;
    Reader status = R.get();
    throwIfImpatient(status);
    if (status.readRequested) return null;

    tryReadLock(status);
    return status;
  }

  void endRead(Reader status) {
    checkReadThreadAccess();
    status.readRequested = false;
    if (writeRequested) {
      LockSupport.unpark(writeThread);  // parked by writeLock()
    }
  }

  private void waitABit(Reader status, int iteration) {
    if (iteration > SPIN_TO_WAIT_FOR_LOCK) {
      status.blocked = true;
      try {
        throwIfImpatient(status);
        LockSupport.parkNanos(this, 1_000_000);  // unparked by writeUnlock
      }
      finally {
        status.blocked = false;
      }
    }
    else {
      Thread.yield();
    }
  }

  private void throwIfImpatient(Reader status) throws ApplicationUtil.CannotRunReadActionException {
    // when client explicitly runs in non-cancelable block do not throw from within nested read actions
    if (status.impatientReads && writeRequested && !ProgressManager.getInstance().isInNonCancelableSection()) {
      throw ApplicationUtil.CannotRunReadActionException.create();
    }
  }

  boolean isInImpatientReader() {
    return R.get().impatientReads;
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

  private boolean tryReadLock(Reader status) {
    throwIfImpatient(status);
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

  void writeIntentLock() {
    checkWriteThreadAccess();
    for (int iter=0; ;iter++) {
      if (writeIntent.compareAndSet(false, true)) {
        assert !writeRequested;
        assert !writeAcquired;

        break;
      }

      if (iter > SPIN_TO_WAIT_FOR_LOCK) {
        LockSupport.parkNanos(this, 1_000_000);  // unparked by writeIntentUnlock
      }
      else {
        Thread.yield();
      }
    }
  }

  void writeIntentUnlock() {
    checkWriteThreadAccess();

    assert !writeAcquired;
    assert !writeRequested;

    writeIntent.set(false);
    LockSupport.unpark(writeThread);
  }

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
        LockSupport.parkNanos(this, 1_000_000);  // unparked by readUnlock
      }
      else {
        Thread.yield();
      }
    }
  }

  void writeSuspendWhilePumpingIdeEventQueueHopingForTheBest(@NotNull Runnable runnable) {
    boolean prev = writeSuspended;
    writeSuspended = true;
    writeUnlock();
    try {
      runnable.run();
    }
    finally {
      cancelActionsToBeCancelledBeforeWrite();
      writeLock();
      writeSuspended = prev;
    }
  }

  void writeUnlock() {
    checkWriteThreadAccess();
    writeAcquired = false;
    writeRequested = false;
    List<Reader> dead;
    long current = System.nanoTime();
    if (current - deadReadersGCStamp > 1_000_000) {
      dead = new ArrayList<>(readers.size());
      deadReadersGCStamp = current;
    }
    else {
      dead = null;
    }
    for (Reader reader : readers) {
      if (reader.blocked) {
        LockSupport.unpark(reader.thread); // parked by readLock()
      }
      else if (dead != null && !reader.thread.isAlive()) {
        dead.add(reader);
      }
    }
    if (dead != null) {
      readers.removeAll(dead);
    }
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

  @Override
  public String toString() {
    return "ReadMostlyRWLock{" +
           "writeThread=" + writeThread +
           ", writeRequested=" + writeRequested +
           ", writeAcquired=" + writeAcquired +
           ", readers=" + readers +
           ", writeSuspended=" + writeSuspended +
           '}';
  }
}
