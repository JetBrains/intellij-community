// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser
import com.intellij.util.EventDispatcher
import git4idea.history.GitLogUtil
import git4idea.repo.GitRepository
import java.util.*

class GithubPullRequestsChangesLoader(private val project: Project,
                                      progressManager: ProgressManager,
                                      private val branchesFetcher: GithubPullRequestsBranchesFetcher,
                                      private val repository: GitRepository)
  : SingleWorkerProcessExecutor(progressManager, "GitHub PR changes loading breaker"),
    GithubPullRequestsBranchesFetcher.RequestChangedListener {

  private val loadingEventDispatcher = EventDispatcher.create(ChangesLoadingListener::class.java)

  init {
    branchesFetcher.addBranchChangeListener(this, this)
  }

  override fun requestChanged() {
    cancelCurrentTasks()

    val fetchFuture = branchesFetcher.request
    if (fetchFuture == null) {
      loadingEventDispatcher.multicaster.loaderCleared()
    }
    else submit { indicator ->
      try {
        val commits = fetchFuture.get()

        val details = GitLogUtil.collectFullDetails(project, repository.root, "${commits.first}..${commits.second}")
        val changes = CommittedChangesTreeBrowser.zipChanges(details.reversed().flatMap { it.changes })

        runInEdt { if (!indicator.isCanceled) loadingEventDispatcher.multicaster.changesLoaded(changes) }
      }
      catch (pce: ProcessCanceledException) {
        // ignore
      }
      catch (e: Exception) {
        runInEdt { if (!indicator.isCanceled) loadingEventDispatcher.multicaster.errorOccurred(e) }
      }
    }
  }

  fun addLoadingListener(listener: ChangesLoadingListener, disposable: Disposable) =
    loadingEventDispatcher.addListener(listener, disposable)

  interface ChangesLoadingListener : EventListener {
    fun changesLoaded(changes: List<Change>) {}
    fun errorOccurred(error: Throwable) {}
    fun loaderCleared() {}
  }
}