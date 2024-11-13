// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.locks.ReentrantReadWriteLock;

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
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

  protected IndexStorageLockingBase() {
  }

  protected @NotNull LockStamp lockForRead() {
    ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    readLock.lock();
    return readLock::unlock;
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
