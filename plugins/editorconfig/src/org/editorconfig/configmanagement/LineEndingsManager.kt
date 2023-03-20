// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.status.LineSeparatorPanel
import com.intellij.util.LineSeparator
import org.editorconfig.Utils
import org.editorconfig.Utils.configValueForKey
import org.editorconfig.plugincomponents.SettingsProviderComponent

class LineEndingsManager : FileDocumentManagerListener {
  companion object {
    // Handles the following EditorConfig settings:
    const val lineEndingsKey = "end_of_line"

    private fun updateStatusBar(project: Project) {
      ApplicationManager.getApplication().invokeLater {
        WindowManager.getInstance()
          .getIdeFrame(project)
          ?.statusBar
          ?.getWidget(StatusBar.StandardWidgets.LINE_SEPARATOR_PANEL)
          .let { widget -> if (widget is LineSeparatorPanel) widget.selectionChanged(null) }
      }
    }
  }

  private var statusBarUpdated = false
  override fun beforeAllDocumentsSaving() {
    statusBarUpdated = false
  }

  override fun beforeDocumentSaving(document: Document) {
    if (ApplicationManager.getApplication().isUnitTestMode) return
    val file = FileDocumentManager.getInstance().getFile(document) ?: return
    val project = ProjectLocator.getInstance().guessProjectForFile(file)
    project?.let { applySettings(it, file) }
  }

  private fun applySettings(project: Project, file: VirtualFile) {
    if (!Utils.isEnabled(CodeStyle.getSettings(project))) {
      return
    }
    val properties = SettingsProviderComponent.getInstance(project).getProperties(file)
    val lineEndings = properties.configValueForKey(lineEndingsKey)
    if (lineEndings.isEmpty()) {
      return
    }
    try {
      val separator = LineSeparator.valueOf(lineEndings.uppercase())
      val oldSeparator = file.detectedLineSeparator
      val newSeparator = separator.separatorString
      if (oldSeparator != newSeparator) {
        file.detectedLineSeparator = newSeparator
        if (!statusBarUpdated) {
          statusBarUpdated = true
          updateStatusBar(project)
        }
      }
    }
    catch (e: IllegalArgumentException) {
      Utils.invalidConfigMessage(project, lineEndings, lineEndingsKey, file.canonicalPath)
    }
  }
}