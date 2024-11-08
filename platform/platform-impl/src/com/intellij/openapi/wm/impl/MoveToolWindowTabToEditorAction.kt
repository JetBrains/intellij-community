// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.IdeBundle
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.addKeyboardAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContextMenuActionBase
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.content.Content
import com.intellij.util.application
import java.awt.event.KeyEvent
import java.util.*
import javax.swing.JComponent
import javax.swing.KeyStroke

private val ORIGINAL_PREFERRED_FOCUSABLE_KEY: Key<JComponent?> =
  Key.create<JComponent>("component.preferredFocusableComponent")

internal class MoveToolWindowTabToEditorAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    if (!Registry.`is`("toolwindow.open.tab.in.editor")) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val fileEditor = e.getData(PlatformDataKeys.FILE_EDITOR)
    val toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW)
    val content = toolWindow?.let { ToolWindowContextMenuActionBase.getContextContent(e, it) }
    val enabled = content != null && toolWindow.id != ToolWindowId.STRUCTURE_VIEW ||
                  fileEditor?.file is ToolWindowTabFileImpl

    e.presentation.isEnabledAndVisible = enabled
    if (!enabled) return

    e.presentation.text = when {
      content != null && content.component !is Placeholder -> templateText
      else -> ActionsBundle.message("action.MoveToolWindowTabToEditorAction.reverse.text")
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val fileEditor = e.getData(PlatformDataKeys.FILE_EDITOR)
    val vFile = fileEditor?.file

    if (vFile is ToolWindowTabFileImpl) {
      val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(vFile.toolWindowId)
      toolWindow ?: return

      val content = toolWindow.contentManager.contents.find { (it.component as? Placeholder)?.file == vFile }
      content ?: return

      toolWindow.activate {
        toolWindow.contentManager.setSelectedContent(content, true)
        moveContentBackToTab(project, content, vFile)
      }
    }
    else {
      val toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW)
      val content = toolWindow?.let { ToolWindowContextMenuActionBase.getContextContent(e, it) }

      content ?: return

      val contentComponent = content.component
      if (contentComponent is Placeholder) {
        moveContentBackToTab(project, content, contentComponent.file)
      }
      else {
        moveContentToEditor(toolWindow, content, project)
      }
    }
  }

  private fun moveContentToEditor(
    toolWindow: ToolWindow,
    content: Content,
    project: Project,
  ) {
    // some contents are initialized lazily when selected (Git Stash)
    val prevSelection = toolWindow.contentManager.selectedContent
    val tabName = content.tabName?.let { StringUtil.stripHtml(it, false).trim() }
    toolWindow.contentManager.setSelectedContentCB(content).doWhenProcessed {
      val fileName =
        if (tabName.isNullOrBlank() || tabName == toolWindow.stripeTitle) toolWindow.stripeTitle
        else "$tabName (${toolWindow.stripeTitle})"
      val vFile = ToolWindowTabFileImpl(fileName, content.icon ?: toolWindow.icon, toolWindow.id, content.component)
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

  private class Placeholder(
    val project: Project,
    val content: Content,
    val file: ToolWindowTabFileImpl
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
}

private fun moveContentBackToTab(project: Project, content: Content, file: ToolWindowTabFileImpl) {
  FileEditorManager.getInstance(project).closeFile(file)
  application.runWriteAction {
    // events are required when editors are moved to another window
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

  content.component.focusCycleRootAncestor?.focusTraversalPolicy
    ?.getDefaultComponent(content.component)?.requestFocus()
}
