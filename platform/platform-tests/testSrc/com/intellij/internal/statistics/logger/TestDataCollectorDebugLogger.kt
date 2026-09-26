// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.logger

import com.intellij.internal.statistic.eventLog.DataCollectorDebugLogger

object TestDataCollectorDebugLogger : DataCollectorDebugLogger {
  override fun warn(message: String?) = Unit
  override fun warn(message: String?, t: Throwable?) = Unit
  override fun info(message: String?) = Unit
  override fun info(message: String?, t: Throwable?) = Unit
  override fun trace(message: String?) = Unit
  override fun isTraceEnabled(): Boolean = false
}