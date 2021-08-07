// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions

import com.google.gson.GsonBuilder
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.internal.statistic.StatisticsDevKitUtil
import com.intellij.internal.statistic.eventLog.events.EventsSchemeBuilder
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.json.JsonLanguage
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction

class GenerateEventsScheme : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = StatisticsRecorderUtil.isTestModeEnabled("FUS") && e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val eventsScheme = EventsSchemeBuilder.buildEventsScheme()
    val text = GsonBuilder().setPrettyPrinting().create().toJson(eventsScheme)
    val scratchFile = ScratchRootType.getInstance().createScratchFile(project, "statistics_events_scheme.json", JsonLanguage.INSTANCE, text)
    if (scratchFile == null) {
      StatisticsDevKitUtil.showNotification(project, NotificationType.ERROR, "Scratch file creation failed for unknown reasons")
      return
    }

    FileEditorManager.getInstance(project).openFile(scratchFile, true)
  }
}
