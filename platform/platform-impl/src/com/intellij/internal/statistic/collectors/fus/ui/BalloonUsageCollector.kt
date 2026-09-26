// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object BalloonUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("balloons", 7)

  @JvmField
  val BALLOON_SHOWN: EventId1<String?> = GROUP.registerEvent("balloon.shown", BalloonIdField())

  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  private class BalloonIdField : StringEventField("balloon_id") {
    override val validationRule: List<String>
      get() {
        return BalloonIdsHolder.EP_NAME.extensionList.flatMap { it.balloonIds }
      }
  }
}
