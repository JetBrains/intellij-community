// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.byteCodeViewer

import com.intellij.icons.AllIcons
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiClass
import com.intellij.ui.content.ContentFactory
import org.jetbrains.annotations.Nls

internal val JAVA_CLASS_FILE = Key.create<VirtualFile>("JAVA_CLASS_FILE")

internal class ShowBytecodeAction : DumbAwareAction() {
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
    val psiFileInEditor = event.getData(CommonDataKeys.PSI_FILE) ?: return
    val virtualFileInEditor = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val fileTypeInEditor = FileTypeRegistry.getInstance().getFileTypeByFileName(virtualFileInEditor.nameSequence)

    var psiClass: PsiClass? = null
    val classFile = if (fileTypeInEditor == JavaClassFileType.INSTANCE) {
      // The user has a class file opened in the focused editor.
      virtualFileInEditor
    }
    else {
      // The user has a source file opened in the focused editor.
      val psiElement = psiFileInEditor.findElementAt(editor.caretModel.offset)
      if (psiElement == null) {
        project.showErrorNotification(BytecodeViewerBundle.message("could.not.find.class.at.cursor"))
        return
      }
      psiClass = ByteCodeViewerManager.getContainingClass(psiElement)
      if (psiClass == null) {
        project.showErrorNotification(BytecodeViewerBundle.message("could.not.find.class.at.cursor"))
        return
      }
      val javaClassFile = ByteCodeViewerManager.findClassFile(psiClass)
      if (javaClassFile == null) {
        project.showErrorNotification(BytecodeViewerBundle.message("please.build.project"), suggestBuild = true)
        return
      }
      javaClassFile
    }


    val panel = BytecodeToolWindowPanel(project, psiClass, classFile)

    val content = toolWindow.contentManager.contents.firstOrNull { it.getUserData(JAVA_CLASS_FILE) == classFile }
                  ?: ContentFactory.getInstance().createContent(panel, classFile.presentableName, false).apply {
                    description = classFile.presentableUrl // appears on tab hover
                    putUserData(JAVA_CLASS_FILE, classFile)
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

private fun Project.showErrorNotification(@Nls content: String, suggestBuild: Boolean = false) {
  val title = BytecodeViewerBundle.message("bytecode.not.found.title")
  val notification = Notification("Bytecode Viewer Errors", title, content, NotificationType.WARNING).setImportant(false)

  val actionManager = ActionManager.getInstance()
  val originalBuildAction = actionManager.getAction("CompileProject")
  if (originalBuildAction != null && suggestBuild) {
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

  notification.notify(this)
  return
}