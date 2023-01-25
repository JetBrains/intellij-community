// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager.Companion.getInstance
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil
import com.intellij.openapi.wm.impl.ProjectFrameHelper
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.ui.*
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Cursor
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.ListCellRenderer
import javax.swing.SwingUtilities

/**
 * @author Konstantin Bulenkov
 */
class FilenameToolbarWidgetAction: DumbAwareAction(), CustomComponentAction {

  override fun actionPerformed(e: AnActionEvent) {
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val hasOpenedFiles = e.project?.let { FileEditorManager.getInstance(it).selectedFiles.isNotEmpty() } ?: false
    val noTabs = UISettings.getInstance().editorTabPlacement == UISettings.TABS_NONE
    e.presentation.isEnabledAndVisible = noTabs && hasOpenedFiles
  }

  override fun createCustomComponent(presentation: Presentation, place: String) = SimpleColoredComponent().apply {
    isOpaque = false
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    object : ClickListener() {
      override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
        if (clickCount == 1) {
          showRecentFilesPopup(this@apply)
          return true
        }
        return false
      }
    }.installOn(this)
  }

  private fun showRecentFilesPopup(component: JComponent) {
    val project = ProjectUtil.getProjectForComponent(component)
    if (project != null) {
      val recentFiles = getInstance(project).fileList.asReversed()
      if (recentFiles.size > 1) {
        val files = recentFiles.subList(1, recentFiles.lastIndex + 1)
        val renderer = SimpleColoredComponent()
        JBPopupFactory.getInstance().createPopupChooserBuilder(files)
          .setRenderer(ListCellRenderer { _, file, _, isSelected, _ -> renderer.apply {
            clear()
            ipad = JBInsets.create(4, 12)
            file as VirtualFile
            applyFor(renderer, file, isSelected, isInToolbar = false)
          } })
          .setItemChosenCallback { t -> FileEditorManager.getInstance(project).openFile(t!!, true) }
          .createPopup()
          .showUnderneathOf(component)
      }
    }
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    component as SimpleColoredComponent
    component.clear()
    applyFor(component, null, false, true)
  }

  private fun applyFor(component: SimpleColoredComponent, f: VirtualFile?, selected: Boolean, isInToolbar: Boolean) {
    val window = SwingUtilities.windowForComponent(component)
    val project = ProjectFrameHelper.getFrameHelper(window)?.project
    if (project != null) {
      val openFiles = FileEditorManager.getInstance(project).selectedFiles
      if (openFiles.isNotEmpty()) {
        val file = f ?: openFiles[0]
        if (file != null) {
          val status = FileStatusManager.getInstance(project).getStatus(file)
          var fg:Color?

          val icon = IconUtil.getIcon(file, Iconable.ICON_FLAG_READ_STATUS, project)
          @Suppress("UseJBColor")
          if (isInToolbar && JBColor.isBright() && ColorUtil.isDark(JBColor.namedColor("MainToolbar.background", Color.WHITE))) {
            component.icon = IconLoader.getDarkIcon(icon, true)
            fg = EditorColorsManager.getInstance().getScheme("Dark").getColor(status.colorKey)
          } else {
            component.icon = icon
            fg = if (selected) null else status.color
          }
          component.iconTextGap = JBUI.scale(4)

          if (fg == null) {
            if (isInToolbar) {
              @Suppress("UnregisteredNamedColor")
              fg = JBColor.namedColor("MainToolbar.Dropdown.foreground", JBColor.foreground())
            } else {
              fg = UIUtil.getListForeground(selected, true)
            }
          }

          @Suppress("HardCodedStringLiteral")
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