// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.gdpr.ConsentConfigurable
import com.intellij.idea.ActionsBundle
import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.devkit.StatisticsDevKitUtil
import com.intellij.internal.statistic.devkit.StatisticsDevKitUtil.DEFAULT_RECORDER
import com.intellij.internal.statistic.devkit.StatisticsDevKitUtil.STATISTICS_NOTIFICATION_GROUP_ID
import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger.getConfig
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger.rollOver
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.options.ex.SingleConfigurableEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem

/**
 * Collects the data from all state collectors and record it in event log.
 *
 * @see com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
 *
 * @see com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
 */
internal class RecordStateStatisticsEventLogAction(private val recorderId: String = DEFAULT_RECORDER,
                                                   private val myShowNotification: Boolean = true) : DumbAwareAction(
  ActionsBundle.message("action.RecordStateCollectors.text"),
  ActionsBundle.message("action.RecordStateCollectors.description"),
  AllIcons.Ide.IncomingChangesOn) {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    if (!checkLogRecordingEnabled(project, DEFAULT_RECORDER)) return

    val message = StatisticsBundle.message("stats.collecting.feature.usages.in.event.log")
    ProgressManager.getInstance().run(object : Backgroundable(project, message, false) {
      override fun run(indicator: ProgressIndicator) {
        rollOver()
        val state = FusStatesRecorder.recordStateAndWait(project, indicator)
        if (state == null) {
          StatisticsDevKitUtil.showNotification(project, NotificationType.ERROR, StatisticsBundle.message("stats.failed.recording.state"))
        }
        else if (myShowNotification) {
          showNotification(project)
        }
      }
    })
  }

  private fun showNotification(project: Project) {
    val logFile = getConfig().getActiveLogFile()
    val virtualFile = if (logFile != null) LocalFileSystem.getInstance().findFileByIoFile(logFile.file) else null
    ApplicationManager.getApplication().invokeLater {
      val notification = Notification(STATISTICS_NOTIFICATION_GROUP_ID, "Finished collecting and recording events", NotificationType.INFORMATION)
      if (virtualFile != null) {
        notification.addAction(NotificationAction.createSimple(
          StatisticsBundle.message("stats.open.log.notification.action"),
          Runnable { FileEditorManager.getInstance(project).openFile(virtualFile, true) }))
      }
      notification.notify(project)
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val isTestMode = recorderId == DEFAULT_RECORDER && StatisticsRecorderUtil.isTestModeEnabled(DEFAULT_RECORDER)
    e.presentation.isEnabled = isTestMode && !FusStatesRecorder.isRecordingInProgress()
  }

  companion object {
    fun checkLogRecordingEnabled(project: Project?, recorderId: String?): Boolean {
      if (StatisticsEventLogProviderUtil.getEventLogProvider(recorderId!!).isRecordEnabled()) {
        return true
      }
      Notification(STATISTICS_NOTIFICATION_GROUP_ID, StatisticsBundle.message("stats.logging.is.disabled"), NotificationType.WARNING)
        .addAction(NotificationAction.createSimple(StatisticsBundle.message("stats.enable.data.sharing"),
                                                   Runnable { SingleConfigurableEditor(project, ConsentConfigurable()).show() }))
        .notify(project)
      return false
    }
  }
}
