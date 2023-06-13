// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.main;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface CancellationManager {
  /**
   * @throws CanceledException if the process has been canceled.
   */
  void checkCanceled() throws CanceledException;

  /**
   * Save result of  {@link CancellationManager#checkCanceled} in one thread.
   * After that this result will be thrown with {@link CancellationManager#checkSavedCancelled} in second thread.
   */
  void saveCancelled();
  void checkSavedCancelled();

  @ApiStatus.Experimental
  class CanceledException extends RuntimeException {

    public CanceledException(@NotNull Throwable cause) {
      super(cause);
    }

  }

  CancellationManager DUMMY = new CancellationManager() {
    @Override
    public void checkCanceled() throws CanceledException {
    }

    @Override
    public void saveCancelled() {
    }

    @Override
    public void checkSavedCancelled() {
    }
  };
}
