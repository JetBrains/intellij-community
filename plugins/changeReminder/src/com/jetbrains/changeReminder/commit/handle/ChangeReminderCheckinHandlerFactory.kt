// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.commit.handle

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.vcs.log.impl.VcsProjectLog
import com.jetbrains.changeReminder.plugin.UserSettings
import com.jetbrains.changeReminder.stats.ChangeReminderEvent
import com.jetbrains.changeReminder.stats.logEvent

class ChangeReminderCheckinHandlerFactory : CheckinHandlerFactory() {
  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    if (!Registry.`is`("vcs.change.reminder.enable") || !ServiceManager.getService(UserSettings::class.java).isPluginEnabled) {
      return CheckinHandler.DUMMY
    }
    val project = panel.project
    logEvent(project, ChangeReminderEvent.HANDLER_REGISTERED)
    val dataManager = VcsProjectLog.getInstance(project).dataManager ?: return CheckinHandler.DUMMY
    val dataGetter = dataManager.index.dataGetter ?: return CheckinHandler.DUMMY
    return if (dataManager.dataPack.isFull) {
      ChangeReminderCheckinHandler(panel, dataManager, dataGetter)
    }
    else {
      CheckinHandler.DUMMY
    }
  }
}