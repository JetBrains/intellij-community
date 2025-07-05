// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.byteCodeViewer

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.ui.content.Content

/**
 * Synchronizes the bytecode viewer with the currently active editor.
 * When the user switches between files in the editor, this service will
 * automatically switch to the corresponding bytecode in the bytecode viewer.
 */
@Service(Service.Level.PROJECT)
internal class BytecodeEditorSynchronizer(private val project: Project) : FileEditorManagerListener {

  /**
   * Called when the selected editor changes.
   * If sync with editor is enabled, this will switch to the corresponding bytecode.
   */
  override fun selectionChanged(event: FileEditorManagerEvent) {
    if (!BytecodeViewerSettings.getInstance().state.syncWithEditor) return

    val virtualFile = event.newFile ?: return
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(BytecodeToolWindowPanel.TOOL_WINDOW_ID) ?: return
    if (!toolWindow.isVisible) return
    val content = findContentForSourceFile(toolWindow.contentManager.contents, virtualFile) ?: return
    toolWindow.contentManager.setSelectedContent(content)
  }

  /**
   * Finds the content pane (tab) in the bytecode viewer tool window that corresponds to the given `sourceFile`.
   */
  private fun findContentForSourceFile(contents: Array<Content>, sourceFile: VirtualFile): Content? {
    val psiFile = PsiManager.getInstance(project).findFile(sourceFile) ?: return null
    if (psiFile !is PsiJavaFile) return null
    for (psiClass in psiFile.classes) {
      val classFile = ByteCodeViewerManager.findClassFile(psiClass) ?: continue
      val content = contents.firstOrNull { it.getUserData(JAVA_CLASS_FILE) == classFile } ?: continue
      return content
    }

    return null
  }

  companion object {
    fun getInstance(project: Project): BytecodeEditorSynchronizer {
      return project.getService(BytecodeEditorSynchronizer::class.java)
    }
  }
}