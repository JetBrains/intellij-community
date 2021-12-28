// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

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

  Level getLevel() {
    return myLevel;
  }
}
