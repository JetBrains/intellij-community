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
    val context = file.context
    return context.pullRequest != null && context.pullRequestDataProvider != null && context.pullRequestDetails != null
  }

  override fun createEditor(project: Project, file: VirtualFile): GHPRFileEditor {
    file as GHPRVirtualFile
    val context = file.context

    return GHPRFileEditor(ProgressManager.getInstance(), FileTypeRegistry.getInstance(),
                          project, EditorFactory.getInstance(),
                          context.dataContext.securityService,
                          context.dataContext.busyStateTracker,
                          context.dataContext.stateService,
                          context.dataContext.reviewService,
                          context.pullRequestDataProvider!!,
                          context.requestExecutor,
                          context.repositoryCoordinates,
                          context.avatarIconsProviderFactory,
                          context.currentUser,
                          context.pullRequestDetails!!).apply {

      DataManager.registerDataProvider(component, DataProvider {
        if (GithubPullRequestKeys.ACTION_DATA_CONTEXT.`is`(it))
          GHPRActionDataContext.withFixedPullRequest(context, context.pullRequest!!)
        else null
      })
    }
  }

  override fun getEditorTypeId(): String = "GHPR"
  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}