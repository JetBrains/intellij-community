// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Locking for the {@link IndexStorage}.
 * The locking strategy is really shared between inverted ({@link IndexStorage}) and forward
 * ({@link com.intellij.util.indexing.impl.forward.ForwardIndex}) indexes -- even though it is
 * {@link IndexStorage} who is responsible for the actual locking strategy implementation. This
 * is because there is no obvious object to put this 'shared' responsibility into.
 *
 * TODO RC: this interface is not for use outside of IndexStorage: it is public for transition period, to be able to
 * quickly replace multiple uses of UpdatableIndex.getLock() across the codebase -> should have fewer and fewer uses
 * with time
 */
@ApiStatus.Internal
public interface IndexStorageLock {

  @NotNull IndexStorageLock.LockStamp lockForRead();

  @NotNull IndexStorageLock.LockStamp lockForWrite();

  default <R, E extends Exception> R withReadLock(@NotNull ThrowableComputable<R, E> computation) throws E {
    try (LockStamp stamp = lockForRead()) {
      return computation.compute();
    }
  }

  default <E extends Exception> void withReadLock(@NotNull ThrowableRunnable<E> computation) throws E {
    try (LockStamp stamp = lockForRead()) {
      computation.run();
    }
  }

  default <E extends Exception> void withWriteLock(@NotNull ThrowableRunnable<E> computation) throws E {
    try (LockStamp stamp = lockForWrite()) {
      computation.run();
    }
  }

  interface LockStamp extends AutoCloseable {
    //overridden to remove 'throws' clause from close()
    @Override
    void close();
  }
}
