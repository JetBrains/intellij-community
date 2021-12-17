// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.diagnostic.JulLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.logging.Level;

public final class TestLogger extends JulLogger {
  @SuppressWarnings({"UnnecessaryFullyQualifiedName", "deprecation"})
  TestLogger(@NotNull String category) {
    super(java.util.logging.Logger.getLogger(category));
  }

  static final class TestLoggerAssertionError extends AssertionError {
    private TestLoggerAssertionError(String message, Throwable cause) {
      super(message, cause);
    }
  }

  @Override
  public void error(String message, @Nullable Throwable t, String @NotNull ... details) {
    if (LoggedErrorProcessor.getInstance().processError(myLogger.getName(), message, ensureNotControlFlow(t), details)) {
      if (t instanceof TestLoggerAssertionError && message.equals(t.getMessage()) && details.length == 0) {
        throw (TestLoggerAssertionError)t;
      }

      if (TestLoggerFactory.shouldSplitTestLogs()) {
        TestLoggerFactory.log(Level.SEVERE.toString(), myLogger.getName(), message, t);
        for (String detail : details) {
          TestLoggerFactory.log(Level.SEVERE.toString(), myLogger.getName(), detail, null);
        }
      }

      message += DefaultLogger.attachmentsToString(t);
      myLogger.log(Level.INFO, message, t);

      DefaultLogger.dumpExceptionsToStderr(message, t, details);

      throw new TestLoggerAssertionError(message, t);
    }
  }

  @Override
  public void warn(String message, @Nullable Throwable t) {
    if (LoggedErrorProcessor.getInstance().processWarn(myLogger.getName(), message, ensureNotControlFlow(t))) {
      if (TestLoggerFactory.shouldSplitTestLogs()) {
        TestLoggerFactory.log(Level.WARNING.toString(), myLogger.getName(), message, t);
      }
      myLogger.log(Level.WARNING, message, t);
    }
  }

  @Override
  public void info(String message) {
    info(message, null);
  }

  @Override
  public void info(String message, @Nullable Throwable t) {
    super.info(message, t);
    TestLoggerFactory.log(Level.INFO.toString(), myLogger.getName(), message, t);
  }

  @Override
  public void debug(String message) {
    debug(message, (Throwable)null);
  }

  @Override
  public void debug(@Nullable Throwable t) {
    debug(null, t);
  }

  @Override
  public void debug(String message, @Nullable Throwable t) {
    if (isDebugEnabled()) {
      super.debug(message, t);
      TestLoggerFactory.log(Level.FINE.toString(), myLogger.getName(), message, t);
    }
  }

  @Override
  public void trace(String message) {
    if (isTraceEnabled()) {
      super.trace(message);
      TestLoggerFactory.log(Level.FINER.toString(), myLogger.getName(), message, null);
    }
  }

  @Override
  public void trace(@Nullable Throwable t) {
    if (isTraceEnabled()) {
      super.trace(t);
      TestLoggerFactory.log(Level.FINER.toString(), myLogger.getName(), null, t);
    }
  }

  @Override
  public boolean isDebugEnabled() {
    return !Accessor.isInStressTest() || super.isDebugEnabled();
  }

  /**
   * Calling {@link com.intellij.openapi.application.ex.ApplicationManagerEx#isInStressTest} reflectively to avoid dependency on a platform module
   */
  private static class Accessor {
    private static final @Nullable MethodHandle isInStressTest = getMethodHandle();

    @SuppressWarnings("CallToPrintStackTrace")
    private static MethodHandle getMethodHandle() {
      try {
        Class<?> clazz = Class.forName("com.intellij.openapi.application.ex.ApplicationManagerEx");
        return MethodHandles.publicLookup().findStatic(clazz, "isInStressTest", MethodType.methodType(boolean.class));
      }
      catch (ReflectiveOperationException e) {
        e.printStackTrace();
        return null;
      }
    }

    private static boolean isInStressTest() {
      try {
        return isInStressTest != null && (boolean)isInStressTest.invokeExact();
      }
      catch (Throwable ignored) {
        return false;
      }
    }
  }
}
