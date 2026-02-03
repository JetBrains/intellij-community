// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.idea.ActionsBundle
import com.intellij.internal.statistic.devkit.StatisticsDevKitUtil
import com.intellij.internal.statistic.config.SerializationHelper
import com.intellij.internal.statistic.eventLog.events.scheme.EventsSchemeBuilder
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.json.JsonLanguage
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction

internal class GenerateEventsScheme(private val recorderId: String = StatisticsDevKitUtil.DEFAULT_RECORDER) : DumbAwareAction(
  ActionsBundle.message("action.GenerateEventsScheme.text"),
  ActionsBundle.message("action.GenerateEventsScheme.description"),
  AllIcons.FileTypes.Json) {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null
                               && StatisticsRecorderUtil.isTestModeEnabled("FUS")
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val eventsScheme = EventsSchemeBuilder.buildEventsScheme(recorderId)
    val text = SerializationHelper.serialize(eventsScheme)
    val scratchFile = ScratchRootType.getInstance()
      .createScratchFile(project, "statistics_events_scheme.json", JsonLanguage.INSTANCE, text)

    if (scratchFile == null) {
      StatisticsDevKitUtil.showNotification(project, NotificationType.ERROR, "Scratch file creation failed for unknown reasons")
      return
    }

    FileEditorManager.getInstance(project).openFile(scratchFile, true)
  }
}
