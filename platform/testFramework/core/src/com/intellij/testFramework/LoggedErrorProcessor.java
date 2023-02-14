// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class LoggedErrorProcessor {
  private static LoggedErrorProcessor ourInstance = new LoggedErrorProcessor();

  static @NotNull LoggedErrorProcessor getInstance() {
    return ourInstance;
  }

  /**
   * Sets the processor to {@code newInstance}, executes {@code runnable} with it, and restores the old processor afterwards.
   */
  public static <T extends Throwable> void executeWith(@NotNull LoggedErrorProcessor newInstance, @NotNull ThrowableRunnable<T> runnable) throws T {
    LoggedErrorProcessor oldInstance = getInstance();
    ourInstance = newInstance;
    try {
      runnable.run();
    }
    finally  {
      ourInstance = oldInstance;
    }
  }

  /**
   * Runs {@code runnable} and returns an exception which was logged from it.
   * Reports failures if no errors or more than one error were logged.
   */
  public static @NotNull Throwable executeAndReturnLoggedError(@NotNull Runnable runnable) {
    AtomicReference<Throwable> error = new AtomicReference<>();
    executeWith(new LoggedErrorProcessor() {
      @Override
      public boolean processError(@NotNull String category, @NotNull String message, Throwable t, String @NotNull [] details) {
        Assert.assertNotNull("Unexpected error without Throwable: " + message, t);
        if (!error.compareAndSet(null, t)) {
          Assert.fail("Multiple errors were reported: " + error.get().getMessage() + " and " + t.getMessage());
        }
        return false;
      }
    }, () -> runnable.run());
    Throwable result = error.get();
    Assert.assertNotNull("No errors were logged", result);
    return result;
  }

  /**
   * Should return {@code true} when the message should be logged by {@link TestLoggerFactory.TestLogger} as usual,
   * or {@code false} to signal that the message is "intercepted" and should be ignored.
   *
   * @see TestLoggerFactory.TestLogger#warn(String, Throwable)
   */
  public boolean processWarn(@NotNull String category, @NotNull String message, @Nullable Throwable t) {
    return true;
  }

  public enum Action {
    LOG, STDERR, RETHROW;
    public static final EnumSet<Action> ALL = EnumSet.allOf(Action.class);
    public static final EnumSet<Action> NONE = EnumSet.noneOf(Action.class);
  }

  /**
   * Returns a set of actions to be performed by {@link TestLoggerFactory.TestLogger#error(String, Throwable, String...)} on the given log event.
   */
  @NotNull
  public Set<Action> processError(@NotNull String category, @NotNull String message, String @NotNull [] details, @Nullable Throwable t) {
    var process = processError(category, message, t, details);
    return process ? Action.ALL : Action.NONE;
  }

  /** @deprecated use/override {@link #processError(String, String, String[], Throwable)} instead */
  @Deprecated(forRemoval = true)
  public boolean processError(@NotNull String category, @NotNull String message, @Nullable Throwable t, String @NotNull [] details) {
    return true;
  }
}
