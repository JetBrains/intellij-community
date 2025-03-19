// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.actions

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.devkit.StatisticsDevKitUtil
import com.intellij.internal.statistic.devkit.StatisticsDevKitUtil.showNotification
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.BaseEventLogMetadataPersistence
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.EventLogMetadataPersistence
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.EventLogMetadataSettingsPersistence
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import java.nio.file.Path

internal class OpenEventsSchemeFileAction : DumbAwareAction {
  private val recorderId: String

  @Suppress("unused")
  constructor() {
    this.recorderId = StatisticsDevKitUtil.DEFAULT_RECORDER
  }

  @Suppress("ActionPresentationInstantiatedInCtor")
  constructor(recorderId: String) : super(StatisticsBundle.message("stats.open.0.scheme.file", recorderId),
                                          ActionsBundle.message("action.OpenEventsSchemeFileAction.description"),
                                          AllIcons.FileTypes.Config) {
    this.recorderId = recorderId
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = event.project != null
                                   && StatisticsRecorderUtil.isTestModeEnabled(recorderId)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    Manager.openFileInEditor(Manager.getEventsSchemeFile(recorderId), project)
  }

  internal object Manager {
    fun openFileInEditor(file: Path, project: Project) {
      val virtualFile = VfsUtil.findFile(file, true)
      if (virtualFile == null) {
        showNotification(project, NotificationType.WARNING, StatisticsBundle.message("stats.file.0.does.not.exist", file.toString()))
        return
      }
      FileEditorManager.getInstance(project).openFile(virtualFile, true)
    }

    fun getEventsSchemeFile(recorderId: String): Path {
      val settings = EventLogMetadataSettingsPersistence.getInstance().getPathSettings(recorderId)
      return if (settings != null && settings.isUseCustomPath) {
        Path.of(settings.customPath)
      }
      else {
        BaseEventLogMetadataPersistence.getDefaultMetadataFile(recorderId, EventLogMetadataPersistence.EVENTS_SCHEME_FILE, null)
      }
    }
  }
}