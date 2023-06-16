// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.main;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.decompiler.struct.StructClass;

@ApiStatus.Experimental
public interface CancellationManager {
  /**
   * @throws CanceledException if the process has been canceled.
   */
  default void checkCanceled() throws CanceledException {
    checkCanceled(null);
  }

  /**
   * @throws CanceledException if the process has been canceled.
   */
  void checkCanceled(@Nullable StructClass classStruct) throws CanceledException;

  /**
   * Called every time the body of a new method is started to be decompiled
   */
  void startMethod(String className, String methodName);

  /**
   * Called every time the method decompilation is finished
   */
  void finishMethod(String className, String methodName);

  @ApiStatus.Experimental
  class CanceledException extends RuntimeException {

    public CanceledException(@NotNull Throwable cause) {
      super(cause);
    }

    public CanceledException() {
      super();
    }
  }

  @ApiStatus.Experimental
  class TimeExceedException extends CanceledException {
  }

  static CancellationManager getSimpleWithTimeout(int maxMethodTimeoutSec) {
    return new TimeoutCancellationManager(maxMethodTimeoutSec);
  }

  class TimeoutCancellationManager implements CancellationManager {
    private final long maxMilis;
    private long startMilis = 0;

    protected TimeoutCancellationManager(int maxMethodTimeoutSec) {
      this.maxMilis = maxMethodTimeoutSec * 1000L;
    }

    @Override
    public void checkCanceled(StructClass classStruct) throws CanceledException {
      if (maxMilis <= 0 || startMilis <= 0) {
        return;
      }
      long timeMillis = System.currentTimeMillis();
      if (timeMillis - startMilis > maxMilis) {
        throw new TimeExceedException();
      }
    }

    @Override
    public void startMethod(String className, String methodName) {
      startMilis = System.currentTimeMillis();
    }

    @Override
    public void finishMethod(String className, String methodName) {
      startMilis = 0;
    }
  }
}
