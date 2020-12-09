// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.internal.statistic.eventLog.validator.storage.ValidationTestRulesPersistedStorage
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction

class CleanupEventsTestSchemeAction(private val recorderId: String? = null)
  : DumbAwareAction(ActionsBundle.message("action.CleanupEventsTestSchemeAction.text"),
                    ActionsBundle.message("action.CleanupEventsTestSchemeAction.description"),
                    AllIcons.Actions.GC) {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Removing Test Scheme", false) {
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