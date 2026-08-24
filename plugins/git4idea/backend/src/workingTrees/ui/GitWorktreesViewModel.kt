// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui

import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch

/** Exposes the Worktrees tab's [entries], recomputed on every [GitRepositoriesHolder] update. */
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
      GitRepositoriesHolder.getInstance(project).updates
        .onSubscription { emit(GitRepositoriesHolder.UpdateType.RELOAD_STATE) }
        .collectLatest { event ->
          when (event) {
            GitRepositoriesHolder.UpdateType.WORKING_TREES_LOADED,
            GitRepositoriesHolder.UpdateType.RELOAD_STATE,
            GitRepositoriesHolder.UpdateType.REPOSITORY_CREATED,
            GitRepositoriesHolder.UpdateType.REPOSITORY_DELETED -> _entries.value = GitWorktreesUiUtil.buildEntries(project)
            else -> {}
          }
        }
    }
  }
}
