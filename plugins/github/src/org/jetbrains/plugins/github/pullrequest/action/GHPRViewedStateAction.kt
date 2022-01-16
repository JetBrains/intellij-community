// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.collaboration.messages.CollaborationToolsBundle.messagePointer
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions.ActionText
import com.intellij.openapi.vcs.FilePath
import com.intellij.vcsUtil.VcsFileUtil.relativePath
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestFileViewedState
import org.jetbrains.plugins.github.api.data.pullrequest.isViewed
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys.GIT_REPOSITORY
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys.PULL_REQUEST_FILES
import java.util.function.Supplier

internal abstract class GHPRViewedStateAction(
  dynamicText: Supplier<@ActionText String>,
  private val isViewed: Boolean
) : DumbAwareAction(dynamicText) {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false

    val repository = e.getData(GIT_REPOSITORY) ?: return
    val files = e.getData(PULL_REQUEST_FILES) ?: return
    val viewedStateProvider = e.getData(PULL_REQUEST_DATA_PROVIDER)?.viewedStateData ?: return
    val viewedState = viewedStateProvider.getViewedState()

    e.presentation.isEnabledAndVisible = files.any { viewedState.isViewed(repository, it) == !isViewed }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val repository = e.getData(GIT_REPOSITORY)!!
    val files = e.getData(PULL_REQUEST_FILES)!!
    val viewedStateProvider = e.getData(PULL_REQUEST_DATA_PROVIDER)!!.viewedStateData
    val viewedState = viewedStateProvider.getViewedState()

    // todo seems we could make all mutations in single request
    for (file in files.filter { viewedState.isViewed(repository, it) == !isViewed }) {
      val repositoryRelativePath = relativePath(repository.root, file)

      viewedStateProvider.updateViewedState(repositoryRelativePath, isViewed)
    }
  }

  private fun Map<String, GHPullRequestFileViewedState>.isViewed(repository: GitRepository, file: FilePath): Boolean? {
    val repositoryRelativePath = relativePath(repository.root, file)

    return this[repositoryRelativePath]?.isViewed()
  }
}

internal class GHPRMarkFilesViewedAction :
  GHPRViewedStateAction(messagePointer("action.CodeReview.MarkChangesViewed.text"), true)

internal class GHPRMarkFilesNotViewedAction :
  GHPRViewedStateAction(messagePointer("action.CodeReview.MarkChangesNotViewed.text"), false)