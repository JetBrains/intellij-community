// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil
import com.intellij.openapi.wm.impl.ProjectFrameHelper
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredComponent
import com.intellij.util.IconUtil
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * @author Konstantin Bulenkov
 */
class FilenameToolbarWidgetAction: DumbAwareAction(), CustomComponentAction {
  override fun actionPerformed(e: AnActionEvent) {

  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = UISettings.getInstance().editorTabPlacement == UISettings.TABS_NONE
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val editorTab = SimpleColoredComponent()
    editorTab.append(presentation.text)
    editorTab.icon = presentation.icon
    return editorTab
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    component as SimpleColoredComponent

    val window = SwingUtilities.windowForComponent(component)
    val project = ProjectFrameHelper.getFrameHelper(window)?.project
    if (project != null) {
      val openFiles = FileEditorManager.getInstance(project).selectedFiles
      if (openFiles.isNotEmpty()) {
        val file = openFiles[0]
        if (file != null) {
          component.clear()
          var fg = FileStatusManager.getInstance(project).getStatus(file).color
          if (fg == null) {
            fg = JBColor.namedColor("MainToolbar.Dropdown.foreground", JBColor.foreground())
          }
          val filename = VfsPresentationUtil.getUniquePresentableNameForUI(project, file)
          component.foreground = fg
          component.isOpaque = false
          component.append(filename)
          val icon = IconUtil.getIcon(file, Iconable.ICON_FLAG_READ_STATUS, project)
          component.icon = IconLoader.getDarkIcon(icon, true)
        }
      }
    }
  }
}