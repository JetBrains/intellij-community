// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneStatus

internal object VcsCloneCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  private val GROUP = EventLogGroup("vcs.clone", 1)

  private val CLONE_STATUS_EVENT_FIELD = EventFields.Enum("status", CloneStatus::class.java)
  private val CLONE_ACTIVITY = GROUP.registerIdeActivity(
    activityName = "cloning",
    finishEventAdditionalFields = arrayOf(CLONE_STATUS_EVENT_FIELD)
  )

  fun cloneStarted(): StructuredIdeActivity {
    return CLONE_ACTIVITY.started(null)
  }

  fun cloneFinished(activity: StructuredIdeActivity, cloneStatus: CloneStatus) {
    activity.finished { listOf(EventPair(CLONE_STATUS_EVENT_FIELD, cloneStatus)) }
  }
}