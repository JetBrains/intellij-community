// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.actions

import com.google.common.collect.Maps
import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.devkit.StatisticsDevKitUtil
import com.intellij.internal.statistic.devkit.toolwindow.ChangedStateEventsPanel
import com.intellij.internal.statistic.devkit.toolwindow.eventLogToolWindowsId
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import com.jetbrains.fus.reporting.model.lion3.LogEvent

internal class ShowChangedStateEventsAction(private val recorderId: String) : DumbAwareAction(
  ActionsBundle.message("action.ShowChangedStateStatisticsAction.text"),
  ActionsBundle.message("action.ShowChangedStateStatisticsAction.description"),
  AllIcons.Actions.Diff) {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    if (!RecordStateStatisticsEventLogAction.checkLogRecordingEnabled(project, recorderId)) return

    val message = StatisticsBundle.message("stats.collecting.feature.usages.in.event.log")
    runBackgroundableTask(message, project, false) { indicator ->
      FeatureUsageLogger.rollOver()
      val oldState = FusStatesRecorder.getCurrentState()
      val newState = FusStatesRecorder.recordStateAndWait(project, indicator)
      if (newState == null) {
        StatisticsDevKitUtil.showNotification(project, NotificationType.ERROR, StatisticsBundle.message("stats.failed.recording.state"))
      }
      else {
        val difference = newState.filter { newEvent -> oldState.all { !isEventsEquals(newEvent, it) } }
        ApplicationManager.getApplication().invokeLater { showResults(project, difference) }
      }
    }
  }

  private fun showResults(project: Project, difference: Collection<LogEvent>) {
    if (difference.isEmpty()) {
      StatisticsDevKitUtil.showNotification(project, NotificationType.INFORMATION, StatisticsBundle.message("stats.no.changed.events"))
      return
    }
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(eventLogToolWindowsId) ?: return
    val displayName = "Changed events: $recorderId"
    val changedEventsComponent = ChangedStateEventsPanel(project, toolWindow.disposable, difference, recorderId).component
    val content = ContentFactory.SERVICE.getInstance().createContent(changedEventsComponent, displayName, true)
    content.preferredFocusableComponent = changedEventsComponent
    val contentManager = toolWindow.contentManager
    contentManager.addContent(content)
    contentManager.setSelectedContent(content)
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isEnabled = recorderId == StatisticsDevKitUtil.DEFAULT_RECORDER && FusStatesRecorder.isComparisonAvailable()
  }

  companion object {
    private val SYSTEM_FIELDS = arrayOf("created", "last", "system_event_id", "system_headless")

    fun isEventsEquals(newEvent: LogEvent, oldEvent: LogEvent): Boolean {
      if (newEvent == oldEvent) return true
      if (newEvent.group != oldEvent.group) return false
      if (newEvent.event.id != oldEvent.event.id) return false
      if (newEvent.event.data.size != oldEvent.event.data.size) return false

      val difference = Maps.difference(newEvent.event.data, oldEvent.event.data)
      for (key in difference.entriesDiffering().keys) {
        if (newEvent.group.id == "settings" && key == "id") continue
        if (key !in SYSTEM_FIELDS) {
          return false
        }
      }

      return true
    }
  }
}
