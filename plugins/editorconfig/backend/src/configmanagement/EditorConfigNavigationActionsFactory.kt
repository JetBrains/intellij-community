// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement

import com.intellij.application.options.codeStyle.cache.CodeStyleCachingService
import com.intellij.editorconfig.common.EditorConfigBundle.message
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.util.containers.toArray
import org.editorconfig.Utils
import org.editorconfig.configmanagement.editor.EditorConfigPreviewManager
import org.editorconfig.language.util.EditorConfigPresentationUtil.getFileName
import org.jetbrains.annotations.Nls

class EditorConfigNavigationActionsFactory private constructor() {
  private val myEditorConfigFilePaths: MutableList<String> = ArrayList()

  fun getNavigationActions(project: Project, sourceFile: VirtualFile): List<AnAction> {
    val actions = synchronized(myEditorConfigFilePaths) {
      val editorConfigFiles = Utils.pathsToFiles(myEditorConfigFilePaths)
      editorConfigFiles.map { file ->
          DumbAwareAction.create(getActionName(file, editorConfigFiles.size > 1)) { openEditorConfig(project, sourceFile, file) }
      }
    }
    return if (actions.size <= 1) actions else listOf(NavigationActionGroup(actions.toArray(AnAction.EMPTY_ARRAY)))
  }

  fun updateEditorConfigFilePaths(editorConfigFilePaths: List<String>) {
    synchronized(myEditorConfigFilePaths) {
      myEditorConfigFilePaths.clear()
      myEditorConfigFilePaths.addAll(editorConfigFilePaths)
    }
  }

  private class NavigationActionGroup(private val myChildActions: Array<AnAction>) : ActionGroup(
    message("action.open.file"), true) {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
      return myChildActions
    }
  }

  companion object {
    private val NAVIGATION_FACTORY_KEY = Key.create<EditorConfigNavigationActionsFactory>("editor.config.navigation.factory")
    private val INSTANCE_LOCK = Any()
    private fun openEditorConfig(project: Project, sourceFile: VirtualFile, editorConfigFile: VirtualFile) {
      val fileEditorManager = FileEditorManager.getInstance(project)
      if (fileEditorManager.isFileOpen(editorConfigFile)) {
        fileEditorManager.closeFile(editorConfigFile)
      }
      EditorConfigPreviewManager.getInstance(project).associateWithPreviewFile(editorConfigFile, sourceFile)
      fileEditorManager.openFile(editorConfigFile, true)
    }

    private fun getActionName(file: VirtualFile, withFolder: Boolean): @Nls String {
      val fileName = getFileName(file, withFolder)
      return if (!withFolder) message("action.open.file") else fileName
    }

    fun getInstance(psiFile: PsiFile): EditorConfigNavigationActionsFactory? {
      val project = psiFile.project
      val file = psiFile.virtualFile
      synchronized(INSTANCE_LOCK) {
        val dataHolder = CodeStyleCachingService.getInstance(project).getDataHolder(file)
        var instance: EditorConfigNavigationActionsFactory? = null
        if (dataHolder != null) {
          instance = dataHolder.getUserData(NAVIGATION_FACTORY_KEY)
          if (instance == null) {
            instance = EditorConfigNavigationActionsFactory()
            dataHolder.putUserData(NAVIGATION_FACTORY_KEY, instance)
          }
        }
        return instance
      }
    }
  }
}