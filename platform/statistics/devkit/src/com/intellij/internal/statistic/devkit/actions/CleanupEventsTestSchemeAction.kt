// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.actions

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.eventLog.validator.storage.ValidationTestRulesPersistedStorage
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil.isAnyTestModeEnabled
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil.isTestModeEnabled
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction

class CleanupEventsTestSchemeAction(private val recorderId: String? = null)
  : DumbAwareAction(ActionsBundle.message("action.CleanupEventsTestSchemeAction.text"),
                    ActionsBundle.message("action.CleanupEventsTestSchemeAction.description"),
                    AllIcons.Actions.GC) {

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = recorderId?.let { isTestModeEnabled(recorderId) } ?: isAnyTestModeEnabled()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project

    ProgressManager.getInstance().run(object : Task.Backgroundable(project, StatisticsBundle.message("stats.removing.test.scheme"), false) {
      override fun run(indicator: ProgressIndicator) {
        if (recorderId == null) {
          ValidationTestRulesPersistedStorage.cleanupAll()
        }
        else {
          ValidationTestRulesPersistedStorage.cleanupAll(listOf(recorderId))
        }
      }
    })
  }
}