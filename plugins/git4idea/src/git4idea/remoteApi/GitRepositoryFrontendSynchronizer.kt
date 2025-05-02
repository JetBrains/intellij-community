// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remoteApi

import com.intellij.util.messages.Topic
import git4idea.repo.GitRepository

/**
 * Notifies about repo-related events inside the [com.intellij.vcs.git.shared.rpc.GitRepositoryApi.getRepositoriesEvents] subscription
 */
internal interface GitRepositoryFrontendSynchronizer {
  fun repositoryCreated(repository: GitRepository)

  fun repositoryUpdated(repository: GitRepository)

  fun tagsLoaded(repository: GitRepository)

  fun favoriteRefsUpdated(repository: GitRepository?)

  companion object {
    @JvmField
    @Topic.ProjectLevel
    val TOPIC: Topic<GitRepositoryFrontendSynchronizer> =
      Topic(GitRepositoryFrontendSynchronizer::class.java, Topic.BroadcastDirection.NONE, true)
  }
}