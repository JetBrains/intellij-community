// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.main;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface CancellationManager {
  /**
   * @throws CanceledException   if the process has been canceled.
   * @throws TimeExceedException if limit timeout is exceeded.
   */
  void checkCanceled() throws CanceledException, TimeExceedException;

  /**
   * @param sec - limit timeout (seconds)
   */
  void setMaxSec(int sec);

  /**
   * Call to start counting down the timeout
   */
  void startMethod();

  /**
   * Call to reset timer
   */
  void finishMethod();

  @ApiStatus.Experimental
  class CanceledException extends RuntimeException {

    public CanceledException(@NotNull Throwable cause) {
      super(cause);
    }
  }

  @ApiStatus.Experimental
  class TimeExceedException extends RuntimeException {
  }

  static CancellationManager getSimpleWithTimeout() {
    return new SimpleWithTimeoutCancellationManager();
  }

  class SimpleWithTimeoutCancellationManager implements CancellationManager {
    private long maxMilis = 0;
    private long startMilis = 0;

    @Override
    public void checkCanceled() throws CanceledException, TimeExceedException {
      if (maxMilis <= 0 || startMilis <= 0) {
        return;
      }
      long timeMillis = System.currentTimeMillis();
      if (timeMillis - startMilis > maxMilis) {
        throw new TimeExceedException();
      }
    }

    @Override
    public void setMaxSec(int sec) {
      maxMilis = sec * 1_000L;
    }

    @Override
    public void startMethod() {
      startMilis = System.currentTimeMillis();
    }

    @Override
    public void finishMethod() {
      startMilis = 0;
    }
  }
}
