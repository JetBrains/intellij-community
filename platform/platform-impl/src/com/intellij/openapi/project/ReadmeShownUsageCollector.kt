// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.util.Key
import java.time.Instant

internal object ReadmeShownUsageCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP: EventLogGroup = EventLogGroup("readme.on.start", 1)
  private val README_CLOSED_EVENT = GROUP.registerEvent("readme.closed", EventFields.DurationMs)

  internal val README_OPENED_ON_START_TS: Key<Instant> = Key.create("readme.shown.timestamp")

  internal fun logReadmeClosedIn(durationMs: Long) {
    README_CLOSED_EVENT.log(durationMs)
  }
}