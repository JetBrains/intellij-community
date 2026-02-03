// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

interface StatisticsSystemEventIdProvider {
  fun getSystemEventId(recorderId: String) : Long

  fun setSystemEventId(recorderId: String, eventId: Long)
}