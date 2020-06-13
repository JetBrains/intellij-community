// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class InternalDataCollectorDebugLogger implements DataCollectorDebugLogger {
  private final Logger myLogger;

  public InternalDataCollectorDebugLogger(Logger logger) {
    myLogger = logger;
  }

  @Override
  public void info(String message) {
    myLogger.info(message);
  }

  @Override
  public void info(@Nullable String message, Throwable t) {
    myLogger.info(message, t);
  }

  @Override
  public void warn(String message) {
    myLogger.warn(message);
  }

  @Override
  public void warn(@Nullable String message, Throwable t) {
    myLogger.warn(message, t);
  }

  @Override
  public void trace(String message) {
    myLogger.trace(message);
  }

  @Override
  public boolean isTraceEnabled() {
    return myLogger.isTraceEnabled();
  }
}
