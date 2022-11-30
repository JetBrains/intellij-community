// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import javax.swing.JComponent

class ProjectToolbarWidgetAction : AnAction(), CustomComponentAction {
  override fun actionPerformed(e: AnActionEvent) {}

  override fun getActionUpdateThread(): ActionUpdateThread  = ActionUpdateThread.BGT

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent = ProjectWidget(presentation)

  override fun update(e: AnActionEvent) {
    val project = e.project

    val showFileNameEnabled = UISettings.getInstance().editorTabPlacement == UISettings.TABS_NONE &&
                              Registry.`is`("ide.experimental.ui.project.widget.show.file")

    val file = if (showFileNameEnabled) e.getData(PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR)?.file else null
    val showFileName = file != null
    val maxLength = if (showFileName) 12 else 24
    val projectName = project?.name ?: ""

    @NlsSafe val fullName = StringBuilder(projectName)
    @NlsSafe val cutName = StringBuilder(cutProject(projectName, maxLength))
    if (showFileName) {
      fullName.append(" — ").append(file!!.name)
      cutName.append(" — ").append(cutFile(file.name, maxLength))
    }
    e.presentation.setText(cutName.toString(), false)
    e.presentation.description = if (cutName.toString() == fullName.toString()) null else fullName.toString()
    e.presentation.putClientProperty(projectKey, project)
    e.presentation.isEnabled = e.isFromActionToolbar
  }

  private fun cutFile(value: String, maxLength: Int): String {
    if (value.length <= maxLength) {
      return value
    }

    val extension = value.substringAfterLast(".", "")
    val name = value.substringBeforeLast(".")
    if (name.length + extension.length <= maxLength) {
      return value
    }

    return name.substring(0, maxLength - extension.length) + "..." + extension
  }

  private fun cutProject(value: String, maxLength: Int): String {
    return if (value.length <= maxLength) value else value.substring(0, maxLength) + "..."
  }
}