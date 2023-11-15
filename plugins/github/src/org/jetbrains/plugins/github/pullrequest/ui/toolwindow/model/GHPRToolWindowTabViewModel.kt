// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model

import com.intellij.collaboration.async.cancelledWith
import com.intellij.collaboration.ui.toolwindow.ReviewTabViewModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import git4idea.remote.hosting.knownRepositories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager

@ApiStatus.Experimental
sealed interface GHPRToolWindowTabViewModel : ReviewTabViewModel {
  @ApiStatus.Experimental
  class PullRequest internal constructor(parentCs: CoroutineScope,
                                         projectVm: GHPRToolWindowProjectViewModel,
                                         id: GHPRIdentifier)
    : GHPRToolWindowTabViewModel, Disposable {
    private val cs = parentCs.childScope().cancelledWith(this)

    override val displayName: String = "#${id.number}"

    val infoVm: GHPRInfoViewModel = projectVm.acquireInfoViewModel(id, this)
    private val _focusRequests = Channel<Unit>(1)
    internal val focusRequests: Flow<Unit> = _focusRequests.receiveAsFlow()

    fun requestFocus() {
      _focusRequests.trySend(Unit)
    }

    fun selectCommit(oid: String) {
      infoVm.detailsVm.value.result?.getOrNull()?.changesVm?.selectCommit(oid)
    }

    fun selectChange(oid: String?, filePath: String) {
      infoVm.detailsVm.value.result?.getOrNull()?.changesVm?.selectChange(oid, filePath)
    }

    override fun dispose() {
      cs.cancel()
    }
  }

  @ApiStatus.Experimental
  class NewPullRequest internal constructor(
    project: Project,
    dataContext: GHPRDataContext
  ) : GHPRToolWindowTabViewModel {
    private val allRepos = project.service<GHHostedRepositoriesManager>().knownRepositories.map { it.repository }

    override val displayName: String =
      GithubBundle.message("tab.title.pull.requests.new",
                           GHUIUtil.getRepositoryDisplayName(allRepos,
                                                             dataContext.repositoryDataService.repositoryCoordinates))

    private val _focusRequests = Channel<Unit>(1)
    internal val focusRequests: Flow<Unit> = _focusRequests.receiveAsFlow()

    fun requestFocus() {
      _focusRequests.trySend(Unit)
    }
  }
}