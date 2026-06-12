// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.report

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications

/**
 * Forces editor notifications to re-run for newly opened XML files so JUnit-report detection can see
 * file content that becomes available only after the editor is attached.
 */
class JUnitXmlFileEditorNotificationRefresher : FileEditorManagerListener, DumbAware {
  override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
    if (!file.isDirectory && file.name.endsWith(".xml", ignoreCase = true)) {
      JUnitReportEditorBannerDismissState.clearDismissState(file)
    }
    scheduleNotificationRefresh(source, file)
  }

  override fun selectionChanged(event: FileEditorManagerEvent) {
    val file = event.newFile ?: return
    scheduleNotificationRefresh(event.manager, file)
  }

  private fun scheduleNotificationRefresh(source: FileEditorManager, file: VirtualFile) {
    if (file.isDirectory || !file.name.endsWith(".xml", ignoreCase = true)) return
    val project = source.project
    if (project.isDisposed) return

    JUnitReportXmlDetector.invalidateEditorDetectionState(file)

    ApplicationManager.getApplication().invokeLater {
      if (!project.isDisposed && file.isValid) {
        EditorNotifications.getInstance(project).updateNotifications(file)
      }
    }
  }
}
