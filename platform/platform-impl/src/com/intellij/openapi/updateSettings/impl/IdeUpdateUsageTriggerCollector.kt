// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class IdeUpdateUsageTriggerCollector : CounterUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("ide.self.update", 3)
    private val DIALOG_SHOWN = GROUP.registerEvent("dialog.shown",
      EventFields.String("patches", listOf("not.available", "manual", "auto")))

    @JvmField
    val NOTIFICATION_SHOWN = GROUP.registerEvent("notification.shown")

    @JvmField
    val NOTIFICATION_CLICKED = GROUP.registerEvent("notification.clicked")

    @JvmField
    val UPDATE_FAILED = GROUP.registerEvent("update.failed")

    @JvmField
    val UPDATE_WHATS_NEW = GROUP.registerEvent("update.whats.new")

    @JvmField
    val UPDATE_STARTED = GROUP.registerEvent("dialog.update.started")

    @JvmField
    val MANUAL_PATCH_PREPARED = GROUP.registerEvent("dialog.manual.patch.prepared")

    @JvmStatic
    fun triggerUpdateDialog(patches: UpdateChain?, isRestartCapable: Boolean) {
      val patchesValue = if (patches == null) {
        "not.available"
      }
      else if (!isRestartCapable) {
        "manual"
      }
      else {
        "auto"
      }
      DIALOG_SHOWN.log(patchesValue)
    }
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }

}