// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Couple
import com.intellij.util.EventDispatcher
import git4idea.commands.Git
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailed
import java.util.*
import java.util.concurrent.Future
import kotlin.properties.Delegates

class GithubPullRequestsBranchesFetcher(progressManager: ProgressManager,
                                        private val git: Git,
                                        private val detailsLoader: GithubPullRequestsDetailsLoader,
                                        private val repository: GitRepository,
                                        private val remote: GitRemote)
  : SingleWorkerProcessExecutor(progressManager, "GitHub PR branch fetching breaker"),
    GithubPullRequestsDetailsLoader.RequestChangedListener {

  @set:CalledInAwt
  var request: Future<Couple<String>>? by Delegates.observable<Future<Couple<String>>?>(null) { _, _, _ ->
    changeEventDispatcher.multicaster.requestChanged()
  }
    private set

  private val changeEventDispatcher = EventDispatcher.create(RequestChangedListener::class.java)

  init {
    detailsLoader.addRequestChangeListener(this, this)
  }

  override fun requestChanged() {
    cancelCurrentTasks()
    request = detailsLoader.request?.let { details ->
      submit {
        val pullRequest = details.get()
        fetchBranch(pullRequest)
      }
    }
  }

  private fun fetchBranch(details: GithubPullRequestDetailed): Couple<String> {
    if (!isCommitFetched(details.head.sha)) {
      git.fetch(repository, remote, emptyList(), "refs/pull/${details.number}/head:").throwOnError()
    }
    if (!isCommitFetched(details.head.sha)) throw IllegalStateException("Pull request head is not available after fetch")
    return Couple.of(details.base.sha, details.head.sha)
  }

  private fun isCommitFetched(commitHash: String): Boolean {
    val result = git.getObjectType(repository, commitHash)
    return result.success() && result.outputAsJoinedString == "commit"
  }

  fun addBranchChangeListener(listener: RequestChangedListener, disposable: Disposable) =
    changeEventDispatcher.addListener(listener, disposable)

  interface RequestChangedListener : EventListener {
    fun requestChanged()
  }
}
