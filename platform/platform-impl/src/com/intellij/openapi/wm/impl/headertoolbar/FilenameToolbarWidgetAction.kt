// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil
import com.intellij.openapi.wm.impl.ProjectFrameHelper
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.IconUtil
import java.awt.Color
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * @author Konstantin Bulenkov
 */
class FilenameToolbarWidgetAction: DumbAwareAction(), CustomComponentAction {
  override fun actionPerformed(e: AnActionEvent) {

  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

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
          val status = FileStatusManager.getInstance(project).getStatus(file)
          var fg:Color?

          val icon = IconUtil.getIcon(file, Iconable.ICON_FLAG_READ_STATUS, project)
          if (JBColor.isBright() && ColorUtil.isDark(JBColor.namedColor("MainToolbar.background", Color.WHITE))) {
            component.icon = IconLoader.getDarkIcon(icon, true)
            fg = EditorColorsManager.getInstance().getScheme("Dark").getColor(status.colorKey)
          } else {
            component.icon = icon
            fg = status.color
          }

          if (fg == null) {
            fg = JBColor.namedColor("MainToolbar.Dropdown.foreground", JBColor.foreground())
          }

          val filename = VfsPresentationUtil.getUniquePresentableNameForUI(project, file)
          component.isOpaque = false
          val hasProblems = WolfTheProblemSolver.getInstance(project).isProblemFile(file)
          val effectColor = if (hasProblems) JBColor.red else null
          val style = when (effectColor) {
            null -> SimpleTextAttributes.STYLE_PLAIN
            else -> SimpleTextAttributes.STYLE_PLAIN or SimpleTextAttributes.STYLE_WAVED
          }

          component.append(filename, SimpleTextAttributes(style, fg, effectColor))
        }
      }
    }
  }
}