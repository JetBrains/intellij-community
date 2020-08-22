// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.util.ArrayUtil;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class LoggedErrorProcessor {
  private static final LoggedErrorProcessor DEFAULT = new LoggedErrorProcessor();

  private static LoggedErrorProcessor ourInstance = DEFAULT;

  @NotNull
  public static LoggedErrorProcessor getInstance() {
    return ourInstance;
  }

  public static void setNewInstance(@NotNull LoggedErrorProcessor newInstance) {
    ourInstance = newInstance;
  }

  public static void restoreDefaultProcessor() {
    ourInstance = DEFAULT;
  }

  public void processWarn(String message, Throwable t, @NotNull Logger logger) {
    logger.warn(message, t);
  }

  public void processError(String message, Throwable t, String[] details, @NotNull Logger logger) {
    if (t instanceof TestLoggerAssertionError && message.equals(t.getMessage()) && ArrayUtil.isEmpty(details)) {
      throw (TestLoggerAssertionError)t;
    }

    message += DefaultLogger.attachmentsToString(t);
    logger.info(message, t);

    DefaultLogger.dumpExceptionsToStderr(message, t, details);

    throw new TestLoggerAssertionError(message, t);
  }

  public void disableStderrDumping(@NotNull Disposable parentDisposable) {
    DefaultLogger.disableStderrDumping(parentDisposable);
  }

  static final class TestLoggerAssertionError extends AssertionError {
    private TestLoggerAssertionError(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
