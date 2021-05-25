// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions

import com.google.gson.GsonBuilder
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.internal.statistic.eventLog.events.EventsSchemeBuilder
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.json.JsonLanguage
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.util.PathUtil

class GenerateEventsScheme : AnAction() {
  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = StatisticsRecorderUtil.isTestModeEnabled("FUS")
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val eventsScheme = EventsSchemeBuilder.buildEventsScheme()
    val text = GsonBuilder().setPrettyPrinting().create().toJson(eventsScheme)
    openInScratch(project, text)
  }

  private fun openInScratch(project: Project, text: String) {
    val fileName = PathUtil.makeFileName("statistics_events_scheme", "json")
    val scratchFile = ScratchRootType.getInstance().createScratchFile(
      project, fileName, JsonLanguage.INSTANCE, text
    )
    if (scratchFile != null) {
      ApplicationManager.getApplication().invokeLater {
        FileEditorManager.getInstance(project).openFile(scratchFile, true)
      }
    }
  }
}