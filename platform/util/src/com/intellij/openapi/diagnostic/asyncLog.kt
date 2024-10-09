// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

internal data class LogEvent(
  val julLogger: java.util.logging.Logger,
  val level: LogLevel,
  val message: String?,
  val throwable: Throwable?,
)

internal fun LogEvent.log() {
  val (julLogger, level, message, throwable) = this
  if (throwable != null) {
    julLogger.log(level.level, message, throwable);
  }
  else {
    julLogger.log(level.level, message);
  }
}
