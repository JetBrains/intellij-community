// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager.Companion.getInstance
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ClickListener
import com.intellij.ui.components.JBLabel
import com.intellij.util.IconUtil
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Cursor
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent

/**
 * @author Konstantin Bulenkov
 */
class FilenameToolbarWidgetAction: DumbAwareAction(), CustomComponentAction {

  companion object {
    const val ID = "main.toolbar.Filename"
  }

  override fun actionPerformed(e: AnActionEvent) {
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    service<FilenameToolbarWidgetAppUpdateService>()
      .updatePresentation(project, FileEditorManager.getInstance(project).selectedFiles.firstOrNull(), e.presentation)
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JBLabel = JBLabel().apply {
    isOpaque = false
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    object : ClickListener() {
      override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
        if (UIUtil.isCloseClick(event, MouseEvent.MOUSE_RELEASED)) {
          val project = ProjectUtil.getProjectForComponent(this@apply)
          if (project != null) {
            val files = FileEditorManager.getInstance(project).selectedFiles
            if (files.isNotEmpty()) {
              FileEditorManager.getInstance(project).closeFile(files[0])
              return true
            }
          }
        }
        if (clickCount == 1) {
          showRecentFilesPopup(this@apply)
          return true
        }
        return false
      }
    }.installOn(this)
    service<FilenameToolbarWidgetAppUpdateService>().registerComponent(this@apply)
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    service<FilenameToolbarWidgetAppUpdateService>().updateComponent(component, presentation)
  }

  private fun showRecentFilesPopup(component: JComponent) {
    val project = ProjectUtil.getProjectForComponent(component)
    if (project != null) {
      val recentFiles = getInstance(project).fileList.asReversed()
      if (recentFiles.size > 1) {
        val files = recentFiles.subList(1, recentFiles.lastIndex + 1)
        JBPopupFactory.getInstance().createListPopup(RecentFilesListPopupStep(project, files))
          .showUnderneathOf(component)
      }
    }
  }

  class RecentFilesListPopupStep(val project: Project, files: List<VirtualFile>) : BaseListPopupStep<VirtualFile>(null, files) {
    override fun getIconFor(value: VirtualFile?): Icon? {
      if (value == null) return null
      return IconUtil.getIcon(value, Iconable.ICON_FLAG_READ_STATUS, project)
    }

    override fun getForegroundFor(value: VirtualFile?): Color? {
      if (value == null) return null
      return FileStatusManager.getInstance(project).getStatus(value).color
    }

    override fun getTextFor(value: VirtualFile?): @NlsSafe String = value?.presentableName ?: ""

    override fun onChosen(selectedValue: VirtualFile?, finalChoice: Boolean): PopupStep<*>? {
      if (selectedValue != null && finalChoice) FileEditorManager.getInstance(project).openFile(selectedValue, true)
      return null
    }
  }
}
