// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions

import com.google.common.collect.Maps
import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.StatisticsDevKitUtil
import com.intellij.internal.statistic.eventLog.LogEvent
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger
import com.intellij.internal.statistic.toolwindow.ChangedStateEventsPanel
import com.intellij.internal.statistic.toolwindow.eventLogToolWindowsId
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

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
        val difference = newState.filter { newEvent -> oldState.all { !isElementsEquals(newEvent, it) } }
        ApplicationManager.getApplication().invokeLater { showResults(project, difference) }
      }
    }
  }

  private fun isElementsEquals(newEvent: LogEvent, oldEvent: LogEvent): Boolean {
    if (newEvent.group != oldEvent.group) return false
    if (newEvent.event.id != oldEvent.event.id) return false
    if (newEvent.event.data.size != oldEvent.event.data.size) return false
    if (newEvent.event.data.keys != oldEvent.event.data.keys) return false

    val difference = Maps.difference(newEvent.event.data, oldEvent.event.data)
    for (key in difference.entriesDiffering().keys) {
      if (key !in SYSTEM_FIELDS) {
        return false
      }
    }

    return true
  }

  private fun showResults(project: Project, difference: Collection<LogEvent>) {
    if (difference.isEmpty()) {
      StatisticsDevKitUtil.showNotification(project, NotificationType.INFORMATION, StatisticsBundle.message("stats.no.changed.events"))
      return
    }
    val contentManager = ToolWindowManager.getInstance(project).getToolWindow(eventLogToolWindowsId)?.contentManager ?: return
    val displayName = "Changed events: $recorderId"
    val eventLogToolWindow = ChangedStateEventsPanel(project, difference)
    val content = ContentFactory.SERVICE.getInstance().createContent(eventLogToolWindow.component, displayName, true)
    content.preferredFocusableComponent = eventLogToolWindow.component
    contentManager.addContent(content)
    contentManager.setSelectedContent(content)
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    event.presentation.isEnabled = recorderId == StatisticsDevKitUtil.DEFAULT_RECORDER && FusStatesRecorder.isComparisonAvailable()
  }

  companion object {
    val SYSTEM_FIELDS = arrayOf("created", "system_event_id")
  }
}
