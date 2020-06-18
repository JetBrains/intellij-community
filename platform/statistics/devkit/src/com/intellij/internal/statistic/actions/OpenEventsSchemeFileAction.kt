// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.StatisticsDevKitUtil
import com.intellij.internal.statistic.StatisticsDevKitUtil.showNotification
import com.intellij.internal.statistic.eventLog.validator.persistence.BaseEventLogWhitelistPersistence
import com.intellij.internal.statistic.eventLog.validator.persistence.EventLogWhitelistPersistence.EVENTS_SCHEME_FILE
import com.intellij.internal.statistic.eventLog.validator.persistence.EventLogWhitelistSettingsPersistence
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

    val settings = EventLogWhitelistSettingsPersistence.getInstance().getPathSettings(myRecorderId)
    val file = if (settings != null && settings.isUseCustomPath) {
      File(settings.customPath)
    }
    else {
      BaseEventLogWhitelistPersistence.getDefaultMetadataFile(myRecorderId, EVENTS_SCHEME_FILE, null)
    }

    openFileInEditor(file, project)
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
  }

}