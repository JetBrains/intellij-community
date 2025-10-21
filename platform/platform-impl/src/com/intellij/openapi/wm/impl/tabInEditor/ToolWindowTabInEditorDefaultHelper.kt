// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.ide.IdeBundle
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.addKeyboardAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContextMenuActionBase
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.content.Content
import com.intellij.util.application
import java.awt.event.KeyEvent
import java.util.*
import javax.swing.JComponent
import javax.swing.KeyStroke

private val ORIGINAL_PREFERRED_FOCUSABLE_KEY: Key<JComponent?> = Key.create<JComponent>("component.preferredFocusableComponent")

internal class ToolWindowTabInEditorDefaultHelper : ToolWindowTabInEditorHelper {
  override fun updatePresentation(e: AnActionEvent, toolWindow: ToolWindow, tabEditorFile: ToolWindowTabFile?) {
    val content = ToolWindowContextMenuActionBase.getContextContent(e)
    val enabled = content != null &&
                  toolWindow.id != ToolWindowId.STRUCTURE_VIEW &&
                  toolWindow.id != ToolWindowId.PROBLEMS_VIEW ||
                  tabEditorFile != null

    e.presentation.isEnabledAndVisible = enabled
    if (!enabled) return

    e.presentation.text = when {
      content != null && content.component !is Placeholder -> ActionsBundle.message("action.MoveToolWindowTabToEditorAction.text")
      else -> ActionsBundle.message("action.MoveToolWindowTabToEditorAction.reverse.text")
    }
    return
  }

  override fun performAction(
    e: AnActionEvent,
    toolWindow: ToolWindow,
    tabEditorFile: ToolWindowTabFile?,
  ) {
    val project = e.project
    if (project == null) return
    if (tabEditorFile != null) {
      val content = toolWindow.contentManager.contents.find { (it.component as? Placeholder)?.file == tabEditorFile }
      content ?: return

      toolWindow.activate {
        toolWindow.contentManager.setSelectedContent(content, true)
        moveContentBackToTab(project, content, tabEditorFile)
      }
    }
    else {
      val toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW)
      val content = ToolWindowContextMenuActionBase.getContextContent(e)

      if (toolWindow == null || content == null) return

      val contentComponent = content.component
      if (contentComponent is Placeholder) {
        moveContentBackToTab(project, content, contentComponent.file)
      }
      else {
        moveContentToEditor(toolWindow, content, project)
      }
    }
    return
  }
}

internal class Placeholder(
  val project: Project,
  val content: Content,
  val file: ToolWindowTabFile,
) : JBPanelWithEmptyText(), Disposable {
  init {
    emptyText.appendLine(IdeBundle.message("status.text.tab.open.in.editor"))
    emptyText.appendLine("")
    emptyText.appendLine(IdeBundle.message("status.text.tab.open.in.editor.jump"), SimpleTextAttributes.LINK_ATTRIBUTES) {
      FileEditorManager.getInstance(project).openFile(file, true)
    }
    emptyText.appendLine(IdeBundle.message("status.text.tab.open.in.editor.restore"), SimpleTextAttributes.LINK_ATTRIBUTES) {
      moveContentBackToTab(project, content, file)
    }
    isFocusable = true
    addKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)) {
      FileEditorManager.getInstance(project).openFile(file, true)
    }
  }

  override fun dispose() {
    moveContentBackToTab(project, content, file)
    if (file.component is Disposable) {
      Disposer.dispose(file.component)
    }
  }
}

internal fun moveContentToEditor(
  toolWindow: ToolWindow,
  content: Content,
  project: Project,
) { // some contents are initialized lazily when selected (Git Stash)
  val prevSelection = toolWindow.contentManager.selectedContent
  val tabName = content.tabName?.let { StringUtil.stripHtml(it, false).trim() }
  toolWindow.contentManager.setSelectedContentCB(content).doWhenProcessed {
    val fileName = if (tabName.isNullOrBlank() || tabName == toolWindow.stripeTitle) toolWindow.stripeTitle
    else "$tabName (${toolWindow.stripeTitle})"
    val vFile = ToolWindowTabFile(fileName, content.icon ?: toolWindow.icon, toolWindow.id, content.component)
    content.component = Placeholder(project, content, vFile)
    val explicitlyRequested = content.preferredFocusableComponent
    if (explicitlyRequested != null && explicitlyRequested !== content.component) {
      content.putUserData(ORIGINAL_PREFERRED_FOCUSABLE_KEY, explicitlyRequested)
    }
    content.preferredFocusableComponent = content.component
    toolWindow.hide {
      prevSelection?.let { toolWindow.contentManager.setSelectedContent(it) }
      FileEditorManager.getInstance(project).openFile(vFile, true)
    }
  }
}

internal fun moveContentBackToTab(project: Project, content: Content, file: ToolWindowTabFile) {
  FileEditorManager.getInstance(project).closeFile(file)
  application.runWriteAction { // events are required when editors are moved to another window
    val publisher = application.getMessageBus().syncPublisher(VirtualFileManager.VFS_CHANGES)
    Collections.singletonList(VFileDeleteEvent(file, file)).let {
      publisher.before(it)
      file.isValid = false
      publisher.after(it)
    }
  }
  content.component = file.component
  content.preferredFocusableComponent = content.getUserData(ORIGINAL_PREFERRED_FOCUSABLE_KEY)
  content.putUserData(ORIGINAL_PREFERRED_FOCUSABLE_KEY, null)

  content.component.focusCycleRootAncestor?.focusTraversalPolicy?.getDefaultComponent(content.component)?.requestFocus()
}
