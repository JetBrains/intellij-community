// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.report

import com.intellij.execution.JUnitBundle
import com.intellij.execution.testframework.sm.runner.history.actions.AbstractImportTestsAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import java.util.function.Function
import javax.swing.JComponent

/**
 * Blue info [EditorNotificationPanel] for JUnit-style XML report files.
 */
class JUnitReportFileEditorNotificationProvider : EditorNotificationProvider, DumbAware {
  override fun collectNotificationData(
    project: Project,
    file: VirtualFile,
  ): Function<in FileEditor, out JComponent?>? {
    if (JUnitReportEditorBannerDismissState.isDismissed(file)) return null

    return when (JUnitReportXmlDetector.detectJUnitReportXmlFile(file, allowDeferredRefresh = true, project = project)) {
      JUnitReportXmlDetector.JUnitReportXmlDetection.MATCH ->
        Function { fileEditor -> JUnitReportEditorNotificationPanel(project, file, fileEditor) }

      JUnitReportXmlDetector.JUnitReportXmlDetection.DEFER_NOTIFICATION_UPDATE,
      JUnitReportXmlDetector.JUnitReportXmlDetection.NO_MATCH,
      -> null
    }
  }
}

private class JUnitReportEditorNotificationPanel(
  private val project: Project,
  private val file: VirtualFile,
  fileEditor: FileEditor,
) : EditorNotificationPanel(fileEditor, Status.Info) {
  init {
    text(JUnitBundle.message("junit.report.file.editor.notification.text"))
    createActionLabel(JUnitBundle.message("junit.report.file.editor.notification.import")) {
      AbstractImportTestsAction.doImport(project, file, null)
    }
    setCloseAction {
      JUnitReportEditorBannerDismissState.dismiss(file)
      EditorNotifications.getInstance(project).updateNotifications(file)
    }
  }
}
