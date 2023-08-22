// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public final class TestTimeOut {
  private final long timeOutMs;

  private TestTimeOut(long ms) {
    timeOutMs = ms;
  }

  @NotNull
  @Contract(pure = true)
  public static TestTimeOut setTimeout(long timeout, @NotNull TimeUnit unit) {
    return new TestTimeOut(System.currentTimeMillis() + unit.toMillis(timeout));
  }

  public boolean timedOut() {
    return timedOut(null);
  }

  public boolean timedOut(Object workProgress) {
    if (isTimedOut()) {
      System.err.println("Timed out. Stopped at "+workProgress);
      return true;
    }
    return false;
  }

  public boolean isTimedOut() {
    return System.currentTimeMillis() > timeOutMs;
  }
}
