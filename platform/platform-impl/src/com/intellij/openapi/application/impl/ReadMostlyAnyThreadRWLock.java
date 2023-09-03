// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl;

import com.intellij.openapi.application.ex.ApplicationUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ConcurrentList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

import static com.intellij.openapi.progress.util.ProgressIndicatorUtils.cancelActionsToBeCancelledBeforeWrite;

/**
 * Read-Write lock optimised for mostly reads.
 * Scales better than {@link java.util.concurrent.locks.ReentrantReadWriteLock} with a number of readers due to reduced contention thanks to thread local structures.
 * The lock has writer preference, i.e. no reader can obtain read lock while there is a writer pending.
 * NOT reentrant.
 * This implementation allows readers and writers live on any thread, there is no dedicated "write thread".
 * <br>
 * The elevator pitch explanation of the algorithm:<br>
 * Read lock: sets state to {@link Reader.State#READ_REQ} in its own thread local {@link Reader} structure and waits for writer to release its lock by checking state of {@link #exclusiveOwner}. On success sets {@link Reader#state} to {@link Reader.State#READ}.<br>
 * Read unlock: sets state to {@link Reader.State#CALM} in its own thread local {@link Reader} structure and wake up thread from {@link #exclusiveOwner}, if here is any.<br>
 * Write lock: wins access to {@link #exclusiveOwner}, sets {@link Reader.State#WRITE} in its onw thread local {@link Reader} structure and waits for all readers (in global {@link #participants} list) to release their read locks by checking {@link Reader#state} for all participants.<br>
 * Write unlock: set {@link #exclusiveOwner} to {@code null}, set state to {@link Reader.State#CALM} in its own thread local {@link Reader} structure, try to wake up any potential writer, if no potential writers wake up all other waiting threads (readers and write intents).<br>
 * Write Intent lock: wins access to {@link #exclusiveOwner}, sets {@link Reader.State#WRITE_INTENT} in its onw thread local {@link Reader} structure.<br>
 * Write Intent unlock: set {@link #exclusiveOwner} to {@code null}, set state to {@link Reader.State#CALM} in its own thread local {@link Reader} structure, try to wake up any potential writer.<br>
 */
final class ReadMostlyAnyThreadRWLock {
  // Thread which hold write or write intent lock now. null if no such locks are taken
  private final AtomicReference<Reader> exclusiveOwner = new AtomicReference<>(null);
  // Exclusive access (which blocks readers too) granted
  private volatile boolean writeAcquired = false;
  // All threads are registered here. Dead participants are garbage collected in writeUnlock().
  private final ConcurrentList<Reader> participants = ContainerUtil.createConcurrentList();

  private volatile boolean writeSuspended;
  // time stamp (nanoTime) of the last check for dead reader threads in writeUnlock().
  // (we have to reduce frequency of this "dead readers GC" activity because Thread.isAlive() turned out to be too expensive)
  private volatile long deadReadersGCStamp;

  ReadMostlyAnyThreadRWLock() {
  }

  // Each reader thread has instance of this struct in its thread local. it's also added to global "readers" list.
  static final class Reader {
    enum State {
      CALM, READ, READ_REQ, WRITE_INTENT, WRITE
    }
    private final @NotNull Thread thread;   // its thread
    // State of this thread
    volatile @NotNull State state;
    private boolean impatientReads; // true if we should throw PCE on contented read lock
    Reader(@NotNull Thread tread) {
      thread = tread;
      state = State.CALM;
    }
    
    private ProcessingContext processingContext;

    @Override
    public @NonNls String toString() {
      return "Reader{" +
             "thread=" + thread +
             ", state=" + state +
             ", impatientReads=" + impatientReads +
             '}';
    }
  }

  private final ThreadLocal<Reader> R = ThreadLocal.withInitial(() -> {
    Reader status = new Reader(Thread.currentThread());
    boolean added = participants.addIfAbsent(status);
    assert added : participants + "; " + Thread.currentThread();
    return status;
  });

  boolean isWriteThread() {
    // Any thread can be the write thread
    return true;
  }

  boolean isReadAllowed() {
    return (isWriteThread() && (isImplicitReadAllowed() || isWriteLocked() || isWriteIntentLocked()))
           || isReadLockedByThisThread();
  }

  boolean isReadLockedByThisThread() {
    Reader status = R.get();
    return status.state == Reader.State.READ;
  }

  // null means lock already acquired, Reader means lock acquired successfully
  Reader startRead() {
    Reader status = R.get();
    // If we hold write or write intent lock, read is allowed
    if (exclusiveOwner.get() == status)
      return null;
    throwIfImpatient(status);
    if (status.state == Reader.State.READ)
      return null;

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
    Reader status = R.get();
    // If we hold write or write intent lock, read is allowed
    if (exclusiveOwner.get() == status)
      return null;
    throwIfImpatient(status);
    if (status.state == Reader.State.READ)
      return null;

    tryReadLock(status);
    return status;
  }

  void endRead(Reader status) {
    if (status != null) {
      status.state = Reader.State.CALM;
      status.processingContext = null;
    }

    // If writer registered itself and wait for stopped readers, wake it up
    Reader waitingWriter = exclusiveOwner.get();
    if (waitingWriter != null && waitingWriter.state == Reader.State.WRITE) {
      LockSupport.unpark(waitingWriter.thread);
    }
  }

  private void waitABit(Reader status, int iteration) {
    if (iteration > SPIN_TO_WAIT_FOR_LOCK) {
      status.state = Reader.State.READ_REQ;
      try {
        throwIfImpatient(status);
        LockSupport.parkNanos(this, 1_000_000);  // unparked by writeUnlock
      }
      finally {
        status.state = Reader.State.CALM;
      }
    }
    else {
      Thread.yield();
    }
  }

  private void throwIfImpatient(Reader status) throws ApplicationUtil.CannotRunReadActionException {
    // when client explicitly runs in non-cancelable block do not throw from within nested read actions
    if (status.impatientReads && exclusiveOwner.get() != null && !ProgressManager.getInstance().isInNonCancelableSection()) {
      throw ApplicationUtil.CannotRunReadActionException.create();
    }
  }

  boolean isInImpatientReader() {
    return R.get().impatientReads;
  }


  ProcessingContext getProcessingContext() {
    Reader reader = R.get();
    if (reader.state == Reader.State.CALM)
      return null;
    ProcessingContext context = reader.processingContext;
    if (context == null) {
      context = reader.processingContext = new ProcessingContext();
    }
    return context;
  }

  <T> T allowProcessingContextInWriteAction(Supplier<T> supplier) {
    /*
    if (Thread.currentThread() != writeThread || writeActionProcessingContext != null) {
      return supplier.get();
    }
    try {
      writeActionProcessingContext = new ProcessingContext();
      return supplier.get();
    }
    finally {
      writeActionProcessingContext = null;
    }
     */
    return supplier.get();
  }

  /**
   * Executes a {@code runnable} in an "impatient" mode.
   * In this mode any attempt to grab read lock
   * will fail (i.e. throw {@link ApplicationUtil.CannotRunReadActionException})
   * if there is a pending write lock request.
   */
  void executeByImpatientReader(@NotNull Runnable runnable) throws ApplicationUtil.CannotRunReadActionException {
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
    Reader exstatus = exclusiveOwner.get();
    if (exstatus == null || exstatus.state != Reader.State.WRITE) {
      status.state = Reader.State.READ;
      exstatus = exclusiveOwner.get();
      if (exstatus == null || exstatus.state != Reader.State.WRITE) {
        return true;
      }
      status.state = Reader.State.CALM;
    }
    return false;
  }

  private static final int SPIN_TO_WAIT_FOR_LOCK = 100;

  void writeIntentLock() {
    Reader status = R.get();
    if (status.state != Reader.State.CALM)
      throw new IllegalStateException(Thread.currentThread() + ": Can not get Write Intent lock when " + status.state + " lock is held");
    status.state = Reader.State.WRITE_INTENT;
    for (int iter=0; ;iter++) {
      if (exclusiveOwner.compareAndSet(null, status)) {
        break;
      }
      if (iter > SPIN_TO_WAIT_FOR_LOCK) {
        LockSupport.parkNanos(this, 1_000_000);  // unparked by writeIntentUnlock or writeUnlock
      }
      else {
        Thread.yield();
      }
    }
  }

  void writeIntentUnlock() {
    Reader status = R.get();
    if (status.state != Reader.State.WRITE_INTENT)
      throw new IllegalStateException(Thread.currentThread() + ": Can not unlock Write Intent lock when status is " + status.state);
    status.state = Reader.State.CALM;
    if (!exclusiveOwner.compareAndSet(status, null))
      throw new IllegalStateException(Thread.currentThread() + ": Can not unlock Write Intent lock, unexpected exclusive participant");
    // Don't need to wakeup readers in any case, as they were not blocked
    unparkPotentialWriters();
  }

  void writeLock() {
    Reader status = R.get();
    if (status.state != Reader.State.CALM && status.state != Reader.State.WRITE_INTENT)
      throw new IllegalStateException(Thread.currentThread() + ": Can not get Write lock when " + status.state + " lock is held");
    Reader.State prevState = status.state;
    status.state = Reader.State.WRITE;
    // Step 1: win "write request" state by register itself as exclusive lock holder
    if (prevState == Reader.State.CALM) {
      for (int iter = 0; ; iter++) {
        if (exclusiveOwner.compareAndSet(null, status)) {
          break;
        }
        if (iter > SPIN_TO_WAIT_FOR_LOCK) {
          LockSupport.parkNanos(this, 1_000_000);  // unparked by writeIntentUnlock() or writeUnlock()
        }
        else {
          Thread.yield();
        }
      }
    } if (exclusiveOwner.get() != status) {
      status.state = prevState;
      throw new IllegalStateException(Thread.currentThread() + ": Can not get Write lock in " + status.state + " state when exclusive owner is " + exclusiveOwner.get());
    }

    // Step 2: wait till all readers are calm
    for (int iter=0; ;iter++) {
      if (areAllReadersIdle()) {
        break;
      }
      if (iter > SPIN_TO_WAIT_FOR_LOCK) {
        LockSupport.parkNanos(this, 1_000_000);  // unparked by readUnlock
      }
      else {
        Thread.yield();
      }
    }
    // Mark for accessor method
    writeAcquired = true;
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
    Reader status = R.get();
    if (status.state != Reader.State.WRITE)
      throw new IllegalStateException(Thread.currentThread() + ": Can not unlock Write lock when status is " + status.state);
    status.state = Reader.State.CALM;
    writeAcquired = false;
    if (!exclusiveOwner.compareAndSet(status, null))
      throw new IllegalStateException(Thread.currentThread() + ": Can not unlock Write lock, unexpected exclusive participant");
    unparkPotentialWritersAndReaders();
  }

  private boolean areAllReadersIdle() {
    for (Reader reader : participants) {
      if (reader.state == Reader.State.READ) {
        return false;
      }
    }
    return true;
  }

  boolean isWriteLocked() {
    return writeAcquired;
  }

  boolean isWriteIntentLocked() {
    Reader status = exclusiveOwner.get();
    return status != null && status.state == Reader.State.WRITE_INTENT;
  }

  boolean isImplicitReadAllowed() {
    return false;
  }

  void setImplicitReadAllowance(boolean enable) {
  }

  private void unparkPotentialWritersAndReaders() {
    if (unparkPotentialWriters())
      return;

    // Wake up all blocked readers
    List<Reader> dead;
    long current = System.nanoTime();
    if (current - deadReadersGCStamp > 1_000_000) {
      dead = new ArrayList<>(participants.size());
      deadReadersGCStamp = current;
    }
    else {
      dead = null;
    }
    for (Reader reader : participants) {
      if (reader.state == Reader.State.READ_REQ) {
        LockSupport.unpark(reader.thread); // parked by waitABit()
        break;
      }
      else if (dead != null && !reader.thread.isAlive()) {
        dead.add(reader);
      }
    }
    if (dead != null) {
      participants.removeAll(dead);
    }
  }

  private boolean unparkPotentialWriters() {
    for (Reader participant : participants) {
      if (participant.state == Reader.State.WRITE) {
        LockSupport.unpark(participant.thread); // parked by writeLock()
        // Don't try to wakeup readers
        return true;
      }
      if (participant.state == Reader.State.WRITE_INTENT) {
        LockSupport.unpark(participant.thread); // parked writeIntentLock()
        // Could wake up readers too
        return false;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return "ReadMostlyAnyThreadRWLock{" +
           "exclusiveOwner=" + exclusiveOwner.get() +
           ", writeAcquired=" + writeAcquired +
           ", implicitRead=" + false +
           ", participants=" + participants +
           ", writeSuspended=" + writeSuspended +
           '}';
  }
}
