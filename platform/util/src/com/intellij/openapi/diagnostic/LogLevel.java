// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

public enum LogLevel {
  OFF(Level.OFF),
  ERROR(Level.SEVERE),
  WARNING(Level.WARNING),
  INFO(Level.INFO),
  DEBUG(Level.FINE),
  TRACE(Level.FINER),
  ALL(Level.ALL);

  private final Level myLevel;

  LogLevel(Level level) {
    myLevel = level;
  }

  @ApiStatus.Internal
  public Level getLevel() {
    return myLevel;
  }

  public String getLevelName() {
    return myLevel.getName();
  }

  public @NotNull String getPrettyLevelName() {
    return getPrettyLevelName(myLevel);
  }

  static @NotNull String getPrettyLevelName(@NotNull Level level) {
    return level == Level.WARNING ? "WARN" : level.getName();
  }
}
