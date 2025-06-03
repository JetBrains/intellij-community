// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl.fus

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields.Int
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

internal object FreezeUiUsageCollector: CounterUsagesCollector() {
  private val GROUP = EventLogGroup(
    id = "freeze.ui",
    recorder = "FUS",
    version = 2,
    description = "Group of events describing behavior of the IDE during UI freeze"
  )

  private val FREEZE_POPUP_SHOWN = GROUP.registerEvent(
    eventId = "freeze.popup.shown",
    description = "Happens when the IDE shows a popup that indicates UI freeze",
  )

  @OptIn(DelicateCoroutinesApi::class)
  internal fun reportUiFreezePopupVisible() {
    GlobalScope.launch { // we don't want to block EDT
      FREEZE_POPUP_SHOWN.log()
    }
  }

  override fun getGroup(): EventLogGroup = GROUP
}