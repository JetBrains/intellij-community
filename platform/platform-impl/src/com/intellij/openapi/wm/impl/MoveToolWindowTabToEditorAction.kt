// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.IdeBundle
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.addKeyboardAction
import com.intellij.openapi.util.Disposer
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
import javax.swing.KeyStroke

internal class MoveToolWindowTabToEditorAction : ToolWindowContextMenuActionBase() {
  override fun update(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    val enabled = content != null && toolWindow.id != ToolWindowId.STRUCTURE_VIEW
    e.presentation.isEnabledAndVisible = enabled
    if (!enabled) return
    e.presentation.text = when {
      content.component !is Placeholder -> templateText
      else -> ActionsBundle.message("action.MoveToolWindowTabToEditorAction.reverse.text")
    }
  }

  override fun actionPerformed(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    content ?: return
    val project = e.project ?: return
    val component = content.component
    if (component is Placeholder) {
      moveContentBackToTab(project, content, component.file)
    }
    else {
      // some contents are initialized lazily when selected (Git Stash)
      val prevSelection = toolWindow.contentManager.selectedContent
      val tabName = content.tabName?.let { StringUtil.stripHtml(it, false).trim() }
      toolWindow.contentManager.setSelectedContentCB(content).doWhenProcessed {
        val fileName =
          if (tabName.isNullOrBlank() || tabName == toolWindow.stripeTitle) toolWindow.stripeTitle
          else "${content.tabName} (${toolWindow.stripeTitle})"
        val vFile = ToolWindowTabFileImpl(fileName, content.icon ?: toolWindow.icon, content.component)
        content.component = Placeholder(project, content, vFile)
        content.preferredFocusableComponent = content.component
        toolWindow.hide {
          prevSelection?.let { toolWindow.contentManager.setSelectedContent(it) }
          FileEditorManager.getInstance(project).openFile(vFile, true)
        }
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
}
