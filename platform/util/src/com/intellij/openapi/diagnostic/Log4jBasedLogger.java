// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diagnostic;

import org.apache.log4j.Level;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Log4jBasedLogger extends Logger {
  protected final org.apache.log4j.Logger myLogger;

  public Log4jBasedLogger(@NotNull org.apache.log4j.Logger delegate) {
    myLogger = delegate;
  }

  @Override
  public boolean isDebugEnabled() {
    return myLogger.isDebugEnabled();
  }

  @Override
  public void debug(@NonNls String message) {
    myLogger.debug(message);
  }

  @Override
  public void debug(@Nullable Throwable t) {
    myLogger.debug("", t);
  }

  @Override
  public void debug(@NonNls String message, @Nullable Throwable t) {
    myLogger.debug(message, t);
  }

  @Override
  public final boolean isTraceEnabled() {
    return myLogger.isTraceEnabled();
  }

  @Override
  public void trace(String message) {
    myLogger.trace(message);
  }

  @Override
  public void trace(@Nullable Throwable t) {
    myLogger.trace("", t);
  }

  @Override
  public void info(@NonNls String message) {
    myLogger.info(message);
  }

  @Override
  public void info(@NonNls String message, @Nullable Throwable t) {
    myLogger.info(message, t);
  }

  @Override
  public void warn(@NonNls String message, @Nullable Throwable t) {
    myLogger.warn(message, t);
  }

  @Override
  public void error(@NonNls String message, @Nullable Throwable t, @NonNls String @NotNull ... details) {
    @NonNls String fullMessage = details.length > 0 ? message + "\nDetails: " + String.join("\n", details) : message;
    myLogger.error(fullMessage, t);
  }

  @Override
  public final void setLevel(@NotNull Level level) {
    myLogger.setLevel(level);
  }
}
