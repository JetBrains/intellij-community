// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

import org.apache.log4j.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DelegatingLogger<T extends Logger> extends Logger {
  protected final T myDelegate;

  protected DelegatingLogger(@NotNull T delegate) {
    myDelegate = delegate;
  }

  @Override
  public boolean isTraceEnabled() {
    return myDelegate.isTraceEnabled();
  }

  @Override
  public void trace(String message) {
    myDelegate.trace(message);
  }

  @Override
  public void trace(@Nullable Throwable t) {
    myDelegate.trace(t);
  }

  @Override
  public boolean isDebugEnabled() {
    return myDelegate.isDebugEnabled();
  }

  @Override
  public void debug(String message) {
    myDelegate.debug(message);
  }

  @Override
  public void debug(@Nullable Throwable t) {
    myDelegate.debug(t);
  }

  @Override
  public void debug(String message, @Nullable Throwable t) {
    myDelegate.debug(message, t);
  }

  @Override
  public void info(String message) {
    myDelegate.info(message);
  }

  @Override
  public void info(String message, @Nullable Throwable t) {
    myDelegate.info(message, t);
  }

  @Override
  public void warn(String message, @Nullable Throwable t) {
    myDelegate.warn(message, t);
  }

  @Override
  public void error(String message, @Nullable Throwable t, String @NotNull ... details) {
    myDelegate.error(message, t, details);
  }

  @Override
  public void setLevel(@NotNull Level level) {
    myDelegate.setLevel(level);
  }

  @Override
  public void setLevel(@NotNull LogLevel level) {
    myDelegate.setLevel(level);
  }
}
