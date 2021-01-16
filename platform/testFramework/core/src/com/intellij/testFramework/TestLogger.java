// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.diagnostic.Log4jBasedLogger;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class TestLogger extends Log4jBasedLogger {
  TestLogger(@NotNull Logger logger) {
    super(logger);
  }

  @Override
  public void warn(String message, @Nullable Throwable t) {
    t = checkException(t);
    LoggedErrorProcessor.getInstance().processWarn(message, t, myLogger);
  }

  @Override
  public void error(String message, @Nullable Throwable t, String @NotNull ... details) {
    t = checkException(t);
    LoggedErrorProcessor.getInstance().processError(message, t, details, myLogger);
  }

  @Override
  public void debug(@NonNls String message) {
    if (isDebugEnabled()) {
      super.debug(message);
      TestLoggerFactory.log(myLogger, Level.DEBUG, message, null);
    }
  }

  @Override
  public void debug(@Nullable Throwable t) {
    if (isDebugEnabled()) {
      super.debug(t);
      TestLoggerFactory.log(myLogger, Level.DEBUG, null, t);
    }
  }

  @Override
  public void debug(@NonNls String message, @Nullable Throwable t) {
    if (isDebugEnabled()) {
      super.debug(message, t);
      TestLoggerFactory.log(myLogger, Level.DEBUG, message, t);
    }
  }

  @Override
  public void info(@NonNls String message) {
    super.info(message);
    TestLoggerFactory.log(myLogger, Level.INFO, message, null);
  }

  @Override
  public void info(@NonNls String message, @Nullable Throwable t) {
    super.info(message, t);
    TestLoggerFactory.log(myLogger, Level.INFO, message, t);
  }

  @Override
  public void trace(String message) {
    if (isTraceEnabled()) {
      super.trace(message);
      TestLoggerFactory.log(myLogger, Level.TRACE, message, null);
    }
  }

  @Override
  public void trace(@Nullable Throwable t) {
    if (isTraceEnabled()) {
      super.trace(t);
      TestLoggerFactory.log(myLogger, Level.TRACE, null, t);
    }
  }

  /**
   * {@link com.intellij.openapi.application.impl.ApplicationInfoImpl} reflective access not to introduce dependency on platform core module
   */
  @SuppressWarnings("CallToPrintStackTrace")
  private static class ApplicationInfoImpl {
    static final ApplicationInfoImpl INSTANCE = new ApplicationInfoImpl();
    private final Method isInStressTestMethod;

    private ApplicationInfoImpl() {
      Method method;
      try {
        Class<?> applicationInfoImplClass = Class.forName("com.intellij.openapi.application.impl.ApplicationInfoImpl");
        method = applicationInfoImplClass.getMethod("isInStressTest");
      }
      catch (ClassNotFoundException | NoSuchMethodException e) {
        e.printStackTrace();
        method = null;
      }
      isInStressTestMethod = method;
    }

    boolean isInStressTest() {
      if (isInStressTestMethod == null) {
        return false;
      }
      try {
        return (boolean)isInStressTestMethod.invoke(null);
      }
      catch (IllegalAccessException | InvocationTargetException e) {
        e.printStackTrace();
        return false;
      }
    }
  }

  @Override
  public boolean isDebugEnabled() {
    if (ApplicationInfoImpl.INSTANCE.isInStressTest()) {
      return super.isDebugEnabled();
    }
    return true;
  }
}
