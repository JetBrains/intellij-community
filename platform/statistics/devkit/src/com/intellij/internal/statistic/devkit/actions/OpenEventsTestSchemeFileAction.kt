// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.actions

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.devkit.StatisticsDevKitUtil
import com.intellij.internal.statistic.devkit.actions.OpenEventsSchemeFileAction.Companion.openFileInEditor
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.EventLogTestMetadataPersistence
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

private class OpenEventsTestSchemeFileAction(private val myRecorderId: String = StatisticsDevKitUtil.DEFAULT_RECORDER)
  : DumbAwareAction(StatisticsBundle.message("stats.open.0.test.scheme.file", myRecorderId),
                    ActionsBundle.message("group.OpenEventsTestSchemeFileAction.description"),
                    AllIcons.FileTypes.Any_type) {
  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = StatisticsRecorderUtil.isTestModeEnabled(myRecorderId)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val file = EventLogTestMetadataPersistence(myRecorderId).eventsTestSchemeFile
    openFileInEditor(file, project)
  }
}