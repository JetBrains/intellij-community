// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class GitBranchesUsageCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private const val VERSION = 1
    private val GROUP: EventLogGroup = EventLogGroup("git.branches", VERSION)

    private val POPUP_CLICKED: EventId = GROUP.registerEvent("popup_widget_clicked")

    @JvmStatic
    fun branchWidgetClicked() {
      POPUP_CLICKED.log()
    }
  }
}