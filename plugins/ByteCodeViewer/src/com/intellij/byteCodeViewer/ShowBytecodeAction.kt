// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.byteCodeViewer

import com.intellij.icons.AllIcons
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

internal val JAVA_CLASS_FILE = Key.create<VirtualFile>("JAVA_CLASS_FILE")

internal class ShowBytecodeAction : AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    event.presentation.setEnabled(event.project != null)
    event.presentation.setIcon(AllIcons.FileTypes.JavaClass)
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val toolWindowManager = ToolWindowManager.getInstance(project)
    val toolWindow = toolWindowManager.getToolWindow(BytecodeToolWindowPanel.TOOL_WINDOW_ID)
                     ?: toolWindowManager.registerToolWindow(BytecodeToolWindowPanel.TOOL_WINDOW_ID) {
                       icon = AllIcons.FileTypes.JavaClass
                       anchor = ToolWindowAnchor.RIGHT
                       hideOnEmptyContent = true
                       canCloseContent = true
                     }
    BytecodeToolWindowService.getInstance(project).ensureContentManagerListenerRegistered(toolWindow)

    // Register the editor synchronizer if not already registered
    project.messageBus.connect(toolWindow.disposable).subscribe(
      topic = FileEditorManagerListener.FILE_EDITOR_MANAGER,
      handler = BytecodeEditorSynchronizer.getInstance(project),
    )

    val editor = event.getData(CommonDataKeys.EDITOR) ?: return
    val psiFile = event.getData(CommonDataKeys.PSI_FILE) ?: return
    val psiElement = psiFile.findElementAt(editor.caretModel.offset) ?: return
    val psiClass = ByteCodeViewerManager.getContainingClass(psiElement) ?: return
    val javaClassFile = ByteCodeViewerManager.findClassFile(psiClass)

    if (javaClassFile == null) {
      val title = BytecodeViewerBundle.message("bytecode.not.found.title")
      val content = BytecodeViewerBundle.message("please.build.project")
      val notification = Notification("Bytecode Viewer Errors", title, content, NotificationType.WARNING).setImportant(false)

      val actionManager = ActionManager.getInstance()
      val originalBuildAction = actionManager.getAction("CompileProject")
      if (originalBuildAction != null) {
        // Wrap the "build project" action because existing ones have various presentations problems:
        // - "Compile" doesn't work
        // - "CompileDirty" works but has only an ugly icon
        // - "CompileProject" works fine but has the wrong text "Rebuild Project"
        val buildAction = object : AnAction(BytecodeViewerBundle.message("build.project")) {
          override fun actionPerformed(e: AnActionEvent) {
            originalBuildAction.actionPerformed(e)
            notification.expire()
          }
        }

        notification.addAction(buildAction)
      }

      notification.notify(project)
      return
    }

    val panel = BytecodeToolWindowPanel(project, psiClass, javaClassFile)

    val content = toolWindow.contentManager.contents.firstOrNull { it.getUserData(JAVA_CLASS_FILE) == javaClassFile }
                  ?: ContentFactory.getInstance().createContent(panel, javaClassFile.presentableName, false).apply {
                    description = javaClassFile.presentableUrl // appears on tab hover
                    putUserData(JAVA_CLASS_FILE, javaClassFile)
                  }


    toolWindow.contentManager.addContent(content)
    content.setDisposer(panel)
    toolWindow.contentManager.setSelectedContent(content)
    toolWindow.setAdditionalGearActions(createActionGroup())
    toolWindow.activate {
      IdeFocusManager.getInstance(project).requestFocus(panel, false)
    }
  }

  private fun createActionGroup(): ActionGroup {
    val showDebugAction = object : ToggleAction(BytecodeViewerBundle.messagePointer("action.show.debug.action.name")) {
      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

      override fun isSelected(e: AnActionEvent): Boolean {
        return BytecodeViewerSettings.getInstance().state.showDebugInfo
      }

      override fun setSelected(e: AnActionEvent, state: Boolean) {
        BytecodeViewerSettings.getInstance().state.showDebugInfo = state
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(BytecodeToolWindowPanel.TOOL_WINDOW_ID) ?: return
        toolWindow.contentManager.contents.forEach {
          val panel = it.component as? BytecodeToolWindowPanel ?: return@forEach
          panel.updateTextInEditor()
        }
      }
    }

    val syncWithEditorAction = object : ToggleAction(BytecodeViewerBundle.messagePointer("action.sync.with.editor.name")) {
      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

      override fun isSelected(e: AnActionEvent): Boolean {
        return BytecodeViewerSettings.getInstance().state.syncWithEditor
      }

      override fun setSelected(e: AnActionEvent, state: Boolean) {
        BytecodeViewerSettings.getInstance().state.syncWithEditor = state
      }
    }

    return DefaultActionGroup(showDebugAction, syncWithEditorAction)
  }
}
