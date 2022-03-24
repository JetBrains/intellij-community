// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project

class GitStashUsageCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private val GROUP: EventLogGroup = EventLogGroup("stash.interactions", 4)

    private val STASH_PUSH = GROUP.registerIdeActivity("stash.push")

    private val STASH_POP = GROUP.registerIdeActivity("stash.pop")

    private val STASH_PUSH_DIALOG = GROUP.registerEvent("stash.push.dialog",
                                                        EventFields.Boolean("message_entered"),
                                                        EventFields.Boolean("keep_index"))
    private val STASH_POP_DIALOG = GROUP.registerEvent("stash.pop.dialog",
                                                       EventFields.Boolean("create_branch"),
                                                       EventFields.Boolean("reinstate_index"),
                                                       EventFields.Boolean("pop_stash"))

    @JvmStatic
    fun logStashPush(project: Project): StructuredIdeActivity {
      return STASH_PUSH.started(project)
    }

    @JvmStatic
    fun logStashPop(project: Project): StructuredIdeActivity {
      return STASH_POP.started(project)
    }

    @JvmStatic
    fun logStashPushDialog(messageEntered: Boolean, keepIndex: Boolean) {
      STASH_PUSH_DIALOG.log(messageEntered, keepIndex)
    }

    @JvmStatic
    fun logStashPopDialog(createBranch: Boolean, reinstateIndex: Boolean, popStash: Boolean) {
      STASH_POP_DIALOG.log(createBranch, reinstateIndex, popStash)
    }
  }
}