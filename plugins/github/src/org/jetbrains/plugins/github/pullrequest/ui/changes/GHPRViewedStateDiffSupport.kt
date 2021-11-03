// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.FilePath
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcsUtil.VcsFileUtil.relativePath
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRViewedStateDataProvider

internal interface GHPRViewedStateDiffSupport {

  @RequiresEdt
  fun markViewed(file: FilePath)

  companion object {
    val KEY: Key<GHPRViewedStateDiffSupport> = Key.create("Github.PullRequest.Diff.ViewedState")
    val PULL_REQUEST_FILE: Key<FilePath> = Key.create("Github.PullRequest.Diff.File")
  }
}

internal class GHPRViewedStateDiffSupportImpl(
  private val repository: GitRepository,
  private val viewedStateData: GHPRViewedStateDataProvider
) : GHPRViewedStateDiffSupport {

  override fun markViewed(file: FilePath) {
    val repositoryRelativePath = relativePath(repository.root, file)

    viewedStateData.updateViewedState(repositoryRelativePath, true)
  }
}