// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl

import com.intellij.icons.ExpUiIcons
import com.intellij.ide.impl.ContentManagerWatcher
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.DiffPreview
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase
import com.intellij.openapi.vcs.changes.ui.SimpleTreeDiffRequestProcessor
import com.intellij.openapi.vcs.changes.ui.SimpleTreeEditorDiffPreview
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.content.Content
import javax.swing.Icon

object ChangesBrowserToolWindow {
  const val TOOLWINDOW_ID: String = "VcsChanges" // NON-NLS

  @JvmStatic
  fun showTab(project: Project, content: Content) {
    val toolWindowManager = ToolWindowManager.getInstance(project)
    val repoToolWindow = toolWindowManager.getToolWindow(TOOLWINDOW_ID) ?: registerRepositoriesToolWindow(toolWindowManager)

    content.putUserData(Content.SIMPLIFIED_TAB_RENDERING_KEY, true)
    repoToolWindow.contentManager.removeAllContents(true)
    repoToolWindow.contentManager.addContent(content)
    repoToolWindow.activate(null)
  }

  @JvmStatic
  fun createDiffPreview(project: Project,
                        changesBrowser: ChangesBrowserBase,
                        disposable: Disposable): DiffPreview {
    val processor = SimpleTreeDiffRequestProcessor(project, "ChangesToolWindowPreview", changesBrowser.viewer, disposable)
    return object : SimpleTreeEditorDiffPreview(processor, changesBrowser.viewer) {
      override fun getCurrentName(): String {
        val changeName = changeViewProcessor.currentChangeName
        return when {
          changeName != null -> VcsBundle.message("changes.editor.diff.preview.title", changeName)
          else -> VcsBundle.message("changes.editor.diff.preview.empty.title")
        }
      }

      override fun returnFocusToTree() {
        ToolWindowManager.getInstance(project).getToolWindow(TOOLWINDOW_ID)?.activate(null)
      }
    }
  }

  private fun registerRepositoriesToolWindow(toolWindowManager: ToolWindowManager): ToolWindow {
    val toolWindow = toolWindowManager.registerToolWindow(RegisterToolWindowTask(
      id = TOOLWINDOW_ID,
      anchor = ToolWindowAnchor.LEFT,
      canCloseContent = true,
      stripeTitle = { VcsBundle.message("ChangesBrowserToolWindow.toolwindow.name") },
      icon = getIcon() // Toolwindow icon won't update without restarting IDE
    ))
    toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true")
    ContentManagerWatcher.watchContentManager(toolWindow, toolWindow.contentManager)
    return toolWindow
  }

  private fun getIcon(): Icon? = if (ExperimentalUI.isNewUI()) ExpUiIcons.Toolwindow.Changes else null
}
