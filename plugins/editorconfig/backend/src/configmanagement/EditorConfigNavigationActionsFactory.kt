// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement

import com.intellij.editorconfig.common.EditorConfigBundle.message
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.toArray
import org.editorconfig.Utils
import org.editorconfig.configmanagement.editor.EditorConfigPreviewManager
import org.editorconfig.language.util.EditorConfigPresentationUtil.getFileName
import org.jetbrains.annotations.Nls

object EditorConfigNavigationActionsFactory {
  fun getNavigationActions(project: Project, sourceFile: VirtualFile): List<AnAction> {
    val editorConfigFiles = Utils.relatedEditorConfigFiles(sourceFile)
    if (editorConfigFiles.isEmpty()) return emptyList()

    val withFolder = editorConfigFiles.size > 1
    val navigationActions = editorConfigFiles.map { editorConfigFile ->
      DumbAwareAction.create(getActionName(editorConfigFile, withFolder)) {
        openEditorConfig(project, sourceFile, editorConfigFile)
      }
    }
    
    return if (withFolder) 
      listOf(NavigationActionGroup(navigationActions.toArray(AnAction.EMPTY_ARRAY))) 
    else 
      navigationActions
  }

  private class NavigationActionGroup(private val myChildActions: Array<AnAction>) : ActionGroup(
    message("action.open.file"), true) {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
      return myChildActions
    }
  }
  
  private fun getActionName(file: VirtualFile, withFolder: Boolean): @Nls String =  
    if (!withFolder)
      message("action.open.file") 
    else 
      getFileName(file, withFolder)

  private fun openEditorConfig(project: Project, sourceFile: VirtualFile, editorConfigFile: VirtualFile) {
    val fileEditorManager = FileEditorManager.getInstance(project)
    if (fileEditorManager.isFileOpen(editorConfigFile)) {
      fileEditorManager.closeFile(editorConfigFile)
    }
    EditorConfigPreviewManager.getInstance(project).associateWithPreviewFile(editorConfigFile, sourceFile)
    fileEditorManager.openFile(editorConfigFile, true)
  }
}
