// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.GHSimpleLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRCommitsViewModel
import org.jetbrains.plugins.github.pullrequest.ui.getResultFlow

internal class GHPRCommitsViewModelImpl(
  scope: CoroutineScope,
  commitsLoadingModel: GHSimpleLoadingModel<List<GHCommit>>,
  securityService: GHPRSecurityService
) : GHPRCommitsViewModel {
  override val ghostUser: GHUser = securityService.ghostUser

  override val reviewCommits: StateFlow<List<GHCommit>> = commitsLoadingModel.getResultFlow()
    .map { commits -> commits?.asReversed() ?: listOf() }
    .stateIn(scope, SharingStarted.Eagerly, listOf())

  private val _selectedCommit: MutableStateFlow<GHCommit?> = MutableStateFlow(null)
  override val selectedCommit: StateFlow<GHCommit?> = _selectedCommit.asStateFlow()

  private val _selectedCommitIndex: MutableStateFlow<Int> = MutableStateFlow(0)
  override val selectedCommitIndex: StateFlow<Int> = _selectedCommitIndex.asStateFlow()

  override fun selectCommit(commit: GHCommit?) {
    _selectedCommit.value = commit
    _selectedCommitIndex.value = reviewCommits.value.indexOf(commit)
  }

  override fun selectAllCommits() {
    _selectedCommit.value = null
    _selectedCommitIndex.value = 0
  }

  override fun selectNextCommit() {
    _selectedCommitIndex.value++
    _selectedCommit.value = reviewCommits.value[_selectedCommitIndex.value]
  }

  override fun selectPreviousCommit() {
    _selectedCommitIndex.value--
    _selectedCommit.value = reviewCommits.value[_selectedCommitIndex.value]
  }
}