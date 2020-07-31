// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface DataCollectorDebugLogger {
  void info(String message);

  void info(String message, Throwable t);

  void warn(String message);

  void warn(String message, Throwable t);

  void trace(String message);

  boolean isTraceEnabled();
}
