// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea

import com.intellij.internal.statistic.IdeActivityDefinition
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.BooleanEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class GitBranchesUsageCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private const val VERSION = 1
    private val GROUP: EventLogGroup = EventLogGroup("git.branches", VERSION)

    private val POPUP_CLICKED: EventId = GROUP.registerEvent("popup_widget_clicked")

    @JvmField
    val IS_BRANCH_PROTECTED: BooleanEventField = EventFields.Boolean("is_protected")

    @JvmField
    val IS_NEW_BRANCH: BooleanEventField = EventFields.Boolean("is_new")

    @JvmField
    val FINISHED_SUCCESSFULLY: BooleanEventField = EventFields.Boolean("successfully")

    @JvmField
    val CHECKOUT_ACTIVITY: IdeActivityDefinition = GROUP.registerIdeActivity(
      "checkout",
      startEventAdditionalFields = arrayOf(IS_BRANCH_PROTECTED, IS_NEW_BRANCH),
      finishEventAdditionalFields = arrayOf(FINISHED_SUCCESSFULLY)
    )

    @JvmField
    val CHECKOUT_OPERATION = GROUP.registerIdeActivity("checkout_operation", parentActivity = CHECKOUT_ACTIVITY)

    @JvmField
    val VFS_REFRESH = GROUP.registerIdeActivity("vfs_refresh", parentActivity = CHECKOUT_ACTIVITY)

    @JvmStatic
    fun branchWidgetClicked() {
      POPUP_CLICKED.log()
    }
  }
}