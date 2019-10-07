// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionDataContext
import org.jetbrains.plugins.github.pullrequest.action.GithubPullRequestKeys

internal class GHPREditorProvider : FileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean {
    if (file !is GHPRVirtualFile) return false
    return file.context.pullRequestDataProvider != null
  }

  override fun createEditor(project: Project, file: VirtualFile): GHPRFileEditor {
    file as GHPRVirtualFile
    val context = file.context

    return GHPRFileEditor(ProgressManager.getInstance(), FileTypeRegistry.getInstance(),
                          project, EditorFactory.getInstance(),
                          file).apply {

      DataManager.registerDataProvider(component, DataProvider {
        if (GithubPullRequestKeys.ACTION_DATA_CONTEXT.`is`(it))
          GHPRActionDataContext.withFixedPullRequest(context, file.pullRequest.number)
        else null
      })
    }
  }

  override fun getEditorTypeId(): String = "GHPR"
  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}