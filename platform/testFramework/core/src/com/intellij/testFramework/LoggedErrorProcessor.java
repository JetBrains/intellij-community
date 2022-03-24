// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

public class LoggedErrorProcessor {
  private static LoggedErrorProcessor ourInstance = new LoggedErrorProcessor();

  static @NotNull LoggedErrorProcessor getInstance() {
    return ourInstance;
  }

  private static void setNewInstance(@NotNull LoggedErrorProcessor newInstance) {
    ourInstance = newInstance;
  }

  /**
   * Set the new {@link LoggedErrorProcessor} {@code newInstance}, execute {@code runnable} with it and restore the old processor afterwards
   */
  public static <T extends Throwable> void executeWith(@NotNull LoggedErrorProcessor newInstance, @NotNull ThrowableRunnable<T> runnable) throws T {
    LoggedErrorProcessor oldInstance = getInstance();
    setNewInstance(newInstance);
    try {
      runnable.run();
    }
    finally  {
      setNewInstance(oldInstance);
    }
  }

  /**
   * Should return {@code true} when the message should be logged by {@link TestLogger} as usual,
   * or {@code false} to signal that the message is "intercepted" and should be ignored.
   *
   * @see TestLogger#warn
   */
  public boolean processWarn(@NotNull String category, String message, Throwable t) {
    return true;
  }

  /**
   * Should return {@code true} when the message should be logged by {@link TestLogger} as usual,
   * or {@code false} to signal that the message is "intercepted" and should be ignored.
   *
   * @see TestLogger#warn
   */
  public boolean processError(@NotNull String category, String message, Throwable t, String @NotNull [] details) {
    return true;
  }
}
