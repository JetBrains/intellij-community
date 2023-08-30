// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup.util

import com.intellij.ide.warmup.WarmupLogger

class DefaultWarmupLogger : WarmupLogger {
  override fun logInfo(message: String) {
    com.intellij.warmup.util.WarmupLogger.logInfo(message)
  }

  override fun logError(message: String, throwable: Throwable?) {
    com.intellij.warmup.util.WarmupLogger.logError(message, throwable)
  }
}