// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

class GitRefreshUsageCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP: EventLogGroup = EventLogGroup("git.status.refresh", 1)

    private val IS_FULL_REFRESH_FIELD = EventFields.Boolean("is_full_refresh")
    private val STATUS_REFRESH = GROUP.registerIdeActivity(activityName = "status.refresh",
                                                           startEventAdditionalFields = arrayOf(IS_FULL_REFRESH_FIELD))
    private val UNTRACKED_REFRESH = GROUP.registerIdeActivity(activityName = "untracked.refresh",
                                                              startEventAdditionalFields = arrayOf(IS_FULL_REFRESH_FIELD))

    @JvmStatic
    fun logStatusRefresh(project: Project, everythingDirty: Boolean): StructuredIdeActivity {
      return STATUS_REFRESH.started(project) {
        listOf(IS_FULL_REFRESH_FIELD.with(everythingDirty))
      }
    }

    @JvmStatic
    fun logUntrackedRefresh(project: Project, everythingDirty: Boolean): StructuredIdeActivity {
      return UNTRACKED_REFRESH.started(project) {
        listOf(IS_FULL_REFRESH_FIELD.with(everythingDirty))
      }
    }
  }
}