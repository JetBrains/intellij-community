// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

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
  private final long endNanos;
  private final long startNanos;

  private TestTimeOut(long timeoutNanos) {
    startNanos = System.nanoTime();
    this.endNanos = startNanos + timeoutNanos;
  }

  @Contract(pure = true)
  public static @NotNull TestTimeOut setTimeout(long timeout, @NotNull TimeUnit unit) {
    return new TestTimeOut(unit.toNanos(timeout));
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
    return System.nanoTime() > endNanos;
  }

  private static CharSequence dumpThreadsWithIndicators() {
    StringBuilder s = new StringBuilder();
    s.append("----all threads---\n");
    for (Thread thread : Thread.getAllStackTraces().keySet()) {
      boolean canceled = CoreProgressManager.isCanceledThread(thread);
      if (canceled) {
        s.append("Thread " + thread + " indicator is canceled\n");
      }
    }
    s.append(ThreadDumper.dumpThreadsToString());
    s.append("----///////---");
    return s;
  }

  public void assertNoTimeout(@NotNull String msg) throws AssertionError {
    if (isTimedOut()) {
      String f = "Timeout during waiting for " + msg + ": " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos) + "ms. " +
                 "thread dump:\n" + dumpThreadsWithIndicators();
      Assert.fail(f);
    }
  }
}
