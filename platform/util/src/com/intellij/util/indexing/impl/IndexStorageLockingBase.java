// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.openapi.progress.Cancellation;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CancellationException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.intellij.util.ConcurrencyUtil.DEFAULT_TIMEOUT_MS;
import static com.intellij.util.SystemProperties.getBooleanProperty;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Locking for {@link IndexStorage} implementations. To be extended by a specific storage implementation, if it needs locking.
 * <p>
 * This class should be seen as a helper, a part of storage implementation -- i.e. it shouldn't be used outside a storage implementation.
 * <p>
 * The locking strategy is really shared between inverted ({@link IndexStorage}) and forward
 * ({@link com.intellij.util.indexing.impl.forward.ForwardIndex}) indexes -- even though it is
 * {@link IndexStorage} who is responsible for the actual locking strategy implementation. This
 * is because there is no obvious object to put this 'shared' responsibility into.
 */
@ApiStatus.Internal
public abstract class IndexStorageLockingBase {
  /**
   * If true, index lookups may throw {@linkplain ProcessCanceledException} if job/indicator is cancelled.
   * Since not all code is ready for PCE from index lookup, this is currently under a feature-flag
   */
  public static final boolean MAKE_INDEX_LOOKUP_CANCELLABLE = getBooleanProperty("intellij.index.cancellable-lookup", true);

  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

  protected IndexStorageLockingBase() {
  }

  protected @NotNull LockStamp lockForRead() throws CancellationException {
    ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    if (MAKE_INDEX_LOOKUP_CANCELLABLE) {
      lockMaybeCancellable(readLock);
    }
    else {
      readLock.lock();
    }
    
    return readLock::unlock;
  }


  private static void lockMaybeCancellable(@NotNull Lock lock) {
    //FIXME RC: this method must be just CancellationUtil.lockMaybeCancellable(lock), but CancellationUtil
    //          ('platform.core') is not available from 'platform.util'.
    //          Why CancellationUtil is in the 'platform.core': because it depends on ProgressManager.checkCanceled().
    //          According to Daniil, dependencies should go other way around: Cancellation.checkCancelled()
    //          should be a central hub for cancellations, and ProgressManager should be installed in Cancellation
    //          -- but this is the topic for a future. Currently Cancellation.checkCancelled() is all we could do
    //          in 'platform.util'
    while (true) {
      //ProgressManager.checkCanceled();
      Cancellation.checkCancelled();
      try {
        if (lock.tryLock(DEFAULT_TIMEOUT_MS, MILLISECONDS)) {
          break;
        }
      }
      catch (InterruptedException e) {
        throw new ProcessCanceledException(e);
      }
    }
  }

  protected @NotNull LockStamp lockForWrite() {
    ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
    writeLock.lock();
    return writeLock::unlock;
  }

  protected <R, E extends Exception> R withReadLock(@NotNull ThrowableComputable<R, E> computation) throws E {
    try (LockStamp ignored = lockForRead()) {
      return computation.compute();
    }
  }

  protected <E extends Exception> void withReadLock(@NotNull ThrowableRunnable<E> computation) throws E {
    try (LockStamp ignored = lockForRead()) {
      computation.run();
    }
  }

  protected <E extends Exception> void withWriteLock(@NotNull ThrowableRunnable<E> computation) throws E {
    try (LockStamp ignored = lockForWrite()) {
      computation.run();
    }
  }

  public interface LockStamp extends AutoCloseable {
    //overridden to remove 'throws' clause from close()
    @Override
    void close();
  }
}
