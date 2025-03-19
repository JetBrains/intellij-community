// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * Simple timer for checking timeouts in tests. How to use:
 * <pre>
 * {@code
 *   TestTimeOut timer = TestTimeOut.setTimeout(2, TimeUnit.SECONDS);
 *   // ... later in the test:
 *   if (timer.isTimedOut()) fail();
 * }
 * </pre>
 */
public final class TestTimeOut {
  private final long endTime;

  private TestTimeOut(long endTime) {
    this.endTime = endTime;
  }

  @Contract(pure = true)
  public static @NotNull TestTimeOut setTimeout(long timeout, @NotNull TimeUnit unit) {
    return new TestTimeOut(System.nanoTime() + unit.toNanos(timeout));
  }

  public boolean timedOut() {
    return timedOut(null);
  }

  public boolean timedOut(Object workProgress) {
    if (isTimedOut()) {
      System.err.println("Timed out. Stopped at " + workProgress);
      return true;
    }
    return false;
  }

  public boolean isTimedOut() {
    return System.nanoTime() > endTime;
  }
}
