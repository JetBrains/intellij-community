// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui

import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import git4idea.workingTrees.GitCreateWorkingTreeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch

private val RELEVANT_UPDATE_TYPES = setOf(
  GitRepositoriesHolder.UpdateType.WORKING_TREES_LOADED,
  GitRepositoriesHolder.UpdateType.RELOAD_STATE,
  GitRepositoriesHolder.UpdateType.REPOSITORY_CREATED,
  GitRepositoriesHolder.UpdateType.REPOSITORY_DELETED,
)

/**
 * Exposes the Worktrees tab's [entries], recomputed on every [GitRepositoriesHolder] update and every change to
 * [GitCreateWorkingTreeService.pendingCreations] - the latter lets a worktree still being created appear as a
 * [GitWorktreeCreatingRow] immediately, without waiting for a repository update.
 */
internal class GitWorktreesViewModel(
  private val project: Project,
  parentCs: CoroutineScope,
) {
  private val cs = parentCs.childScope("GitWorktreesViewModel")

  private val _entries = MutableStateFlow<List<GitWorkingTreesListEntry>>(emptyList())
  val entries: StateFlow<List<GitWorkingTreesListEntry>> = _entries.asStateFlow()

  init {
    cs.launch {
      // onSubscription emits the initial-load sentinel only once the collector is already registered
      // with the replay-0 updates flow, so no update fired in between can be missed.
      val relevantUpdates = GitRepositoriesHolder.getInstance(project).updates
        .onSubscription { emit(GitRepositoriesHolder.UpdateType.RELOAD_STATE) }
        .filter { it in RELEVANT_UPDATE_TYPES }

      combine(relevantUpdates, GitCreateWorkingTreeService.getInstance().pendingCreations) { _, pendingCreations -> pendingCreations }
        .collectLatest { pendingCreations -> _entries.value = GitWorktreesUiUtil.buildEntries(project, pendingCreations) }
    }
  }
}
