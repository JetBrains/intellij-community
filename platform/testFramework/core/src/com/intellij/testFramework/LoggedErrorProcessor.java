// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import org.jetbrains.annotations.NotNull;

public class LoggedErrorProcessor {
  private static final LoggedErrorProcessor DEFAULT = new LoggedErrorProcessor();

  private static LoggedErrorProcessor ourInstance = DEFAULT;

  public static @NotNull LoggedErrorProcessor getInstance() {
    return ourInstance;
  }

  public static void setNewInstance(@NotNull LoggedErrorProcessor newInstance) {
    ourInstance = newInstance;
  }

  public static void restoreDefaultProcessor() {
    ourInstance = DEFAULT;
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
