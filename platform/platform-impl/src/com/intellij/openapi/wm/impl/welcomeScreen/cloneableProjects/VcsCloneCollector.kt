// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneStatus
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneTaskInfo

internal object VcsCloneCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  private val GROUP = EventLogGroup("vcs.clone", 2)
  private val CLONE_STATUS_EVENT_FIELD = EventFields.Enum("status", CloneStatus::class.java)

  @JvmField
  internal val SHALLOW_CLONE_DEPTH = EventFields.Int("status")

  private val CLONE_ACTIVITY = GROUP.registerIdeActivity(
    activityName = "cloning",
    finishEventAdditionalFields = arrayOf(CLONE_STATUS_EVENT_FIELD)
  )

  fun cloneStarted(cloneTaskInfo: CloneTaskInfo): StructuredIdeActivity {
    return CLONE_ACTIVITY.started(null) {
      cloneTaskInfo.getActivityData()
    }
  }

  fun cloneFinished(activity: StructuredIdeActivity, cloneStatus: CloneStatus, cloneTaskInfo: CloneTaskInfo) {
    activity.finished {
      cloneTaskInfo.getActivityData() + CLONE_STATUS_EVENT_FIELD.with(cloneStatus)
    }
  }
}