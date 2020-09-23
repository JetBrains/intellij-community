// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.StatisticsDevKitUtil
import com.intellij.internal.statistic.StatisticsDevKitUtil.showNotification
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.BaseEventLogMetadataPersistence
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.EventLogMetadataPersistence.EVENTS_SCHEME_FILE
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.EventLogMetadataSettingsPersistence
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import java.io.File

class OpenEventsSchemeFileAction(private val myRecorderId: String = StatisticsDevKitUtil.DEFAULT_RECORDER)
  : DumbAwareAction(StatisticsBundle.message("stats.open.0.scheme.file", myRecorderId),
                    ActionsBundle.message("group.OpenEventsSchemeFileAction.description"),
                    AllIcons.FileTypes.Config) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    openFileInEditor(getEventsSchemeFile(myRecorderId), project)
  }

  companion object {
    fun openFileInEditor(file: File, project: Project) {
      val virtualFile = VfsUtil.findFileByIoFile(file, true)
      if (virtualFile == null) {
        showNotification(project, NotificationType.WARNING, StatisticsBundle.message("stats.file.0.does.not.exist", file.absolutePath))
        return
      }
      FileEditorManager.getInstance(project).openFile(virtualFile, true)
    }

    fun getEventsSchemeFile(recorderId: String): File {
      val settings = EventLogMetadataSettingsPersistence.getInstance().getPathSettings(recorderId)
      return if (settings != null && settings.isUseCustomPath) {
        File(settings.customPath)
      }
      else {
        BaseEventLogMetadataPersistence.getDefaultMetadataFile(recorderId, EVENTS_SCHEME_FILE, null)
      }
    }
  }

}