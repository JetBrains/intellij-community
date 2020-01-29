// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

public class EmptyDataCollectorDebugLogger implements DataCollectorDebugLogger {
  @Override
  public void info(String message) {}

  @Override
  public void info(String message, Throwable t) {}

  @Override
  public void warn(String message) {}

  @Override
  public void warn(String message, Throwable t) {}

  @Override
  public void trace(String message) {}

  @Override
  public boolean isTraceEnabled() {
    return false;
  }
}
