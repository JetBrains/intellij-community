// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.annotate.AnnotationWarning
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import java.util.function.Function
import javax.swing.JComponent
import kotlin.math.abs

internal class AnnotateWarningsProvider : EditorNotificationProvider, DumbAware {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<FileEditor, JComponent?>? {
    return Function { editor: FileEditor ->
      val editor = (editor as? TextEditor)?.editor ?: return@Function null
      val warning = editor.getUserData(AnnotateDataKeys.WARNING_DATA) ?: return@Function null
      NotificationPanel(project, editor, warning.warning, warning.forceAnnotate)
    }
  }

  private inner class NotificationPanel(
    private val project: Project,
    private val editor: Editor,
    warning: AnnotationWarning,
    private val forceAnnotate: Runnable,
  ) : EditorNotificationPanel(warning.backgroundColor, warning.status) {
    init {
      text = warning.message

      warning.actions.forEach { action ->
        createActionLabel(action.text) { action.doAction(::hideNotification) }
      }
      if (!warning.showAnnotation) {
        createActionLabel(VcsBundle.message("link.label.display.anyway")) { showAnnotations() }
      }

      setCloseAction(::hideNotification)
    }

    private fun showAnnotations() {
      forceAnnotate.run()
    }

    private fun hideNotification() {
      editor.putUserData(AnnotateDataKeys.WARNING_DATA, null)
      EditorNotifications.getInstance(project).updateNotifications(this@AnnotateWarningsProvider)
    }
  }
}

@Service(Service.Level.PROJECT)
internal class AnnotateWarningsService(private val project: Project) {
  fun getWarning(fileAnnotation: FileAnnotation, upToDateLineNumbers: UpToDateLineNumberProvider): AnnotationWarning? {
    val vcsKey = fileAnnotation.vcsKey ?: return null
    val vcs = ProjectLevelVcsManager.getInstance(project).findVcsByName(vcsKey.name) ?: return null

    val expectedLines = upToDateLineNumbers.getLineCount().coerceAtLeast(1);
    val actualLines = fileAnnotation.getLineCount().coerceAtLeast(1);
    if (abs(expectedLines - actualLines) > 1) { // 1 - for different conventions about files ending with line separator
      LOG.warn("Unexpected annotation lines number. Expected: $expectedLines, actual: $actualLines");
      return AnnotationWarning.error(VcsBundle.message("annotation.wrong.line.number.notification.text", vcs.getDisplayName()))
    }

    return vcs.annotationProvider?.getAnnotationWarnings(fileAnnotation)
  }

  private companion object {
    val LOG = thisLogger()
  }
}