// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
}
