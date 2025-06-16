// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model

import com.intellij.collaboration.ui.toolwindow.ReviewTabViewModel
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateViewModel

@ApiStatus.Experimental
sealed interface GHPRToolWindowTabViewModel : ReviewTabViewModel {
  @ApiStatus.Experimental
  class PullRequest internal constructor(parentCs: CoroutineScope,
                                         projectVm: GHPRToolWindowProjectViewModel,
                                         id: GHPRIdentifier)
    : GHPRToolWindowTabViewModel {
    private val cs = parentCs.childScope(javaClass.name)

    override val displayName: String = "#${id.number}"

    val infoVm: GHPRInfoViewModel = projectVm.acquireInfoViewModel(id, cs)
    private val _focusRequests = Channel<Unit>(1)
    internal val focusRequests: Flow<Unit> = _focusRequests.receiveAsFlow()

    fun requestFocus() {
      _focusRequests.trySend(Unit)
    }

    fun selectCommit(oid: String) {
      infoVm.detailsVm.value.result?.getOrNull()?.changesVm?.selectCommit(oid)
    }
  }

  @ApiStatus.Experimental
  class NewPullRequest internal constructor(internal val createVm: GHPRCreateViewModel) : GHPRToolWindowTabViewModel {
    override val displayName: String = GithubBundle.message("tab.title.pull.requests.new", createVm.repositoryName)

    private val _focusRequests = Channel<Unit>(1)
    internal val focusRequests: Flow<Unit> = _focusRequests.receiveAsFlow()

    fun requestFocus() {
      _focusRequests.trySend(Unit)
    }
  }
}