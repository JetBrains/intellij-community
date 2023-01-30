// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabCommitDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest

internal interface GitLabMergeRequestDetailsCommitsViewModel {
  val reviewCommits: StateFlow<List<GitLabCommitDTO>>
  val selectedCommit: StateFlow<GitLabCommitDTO?>
  val selectedCommitIndex: StateFlow<Int>

  fun selectCommit(commit: GitLabCommitDTO?)

  fun selectAllCommits()

  fun selectNextCommit()

  fun selectPreviousCommit()
}

internal class GitLabMergeRequestDetailsCommitsViewModelImpl(
  scope: CoroutineScope,
  mergeRequest: GitLabMergeRequest
) : GitLabMergeRequestDetailsCommitsViewModel {
  override val reviewCommits: StateFlow<List<GitLabCommitDTO>> = mergeRequest.commits.stateIn(scope, SharingStarted.Lazily, listOf())

  private val _selectedCommit: MutableStateFlow<GitLabCommitDTO?> = MutableStateFlow(null)
  override val selectedCommit: StateFlow<GitLabCommitDTO?> = _selectedCommit.asStateFlow()

  private val _selectedCommitIndex: MutableStateFlow<Int> = MutableStateFlow(0)
  override val selectedCommitIndex: StateFlow<Int> = _selectedCommitIndex.asStateFlow()

  override fun selectCommit(commit: GitLabCommitDTO?) {
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