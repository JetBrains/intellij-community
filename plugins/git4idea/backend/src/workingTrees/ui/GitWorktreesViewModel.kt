// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui

import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.vcs.impl.shared.RepositoryId
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import git4idea.GitStandardLocalBranch
import git4idea.repo.GitRepositoryIdCache
import git4idea.ui.branch.GitBranchReviewPresenter
import git4idea.workingTrees.GitCreateWorkingTreeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update
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

  private val _reviews = MutableStateFlow<Map<GitWorktreeBranchKey, GitBranchReviewPresenter.Review?>>(emptyMap())
  val reviews: StateFlow<Map<GitWorktreeBranchKey, GitBranchReviewPresenter.Review?>> = _reviews.asStateFlow()

  /** Only touched from the single collector coroutine below - one lookup [Job] per repository with live branch keys. */
  private val reviewJobs = HashMap<RepositoryId, Job>()

  /** Only touched from the single collector coroutine below - the branch set each [reviewJobs] entry was started with. */
  private var reviewBranchesByRepository: Map<RepositoryId, Set<GitStandardLocalBranch>> = emptyMap()

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

    // A separate, independent flow - a slow PR/MR lookup must never block the local-git-state rebuild above, and
    // a branch appearing or disappearing elsewhere in the key set must not restart, or lose the result of,
    // another branch's still-running (or already-resolved) lookup.
    cs.launch {
      entries
        .map { it.toBranchKeys() }
        .distinctUntilChanged()
        .collect { keys -> reconcileReviewJobs(keys) }
    }
  }

  private fun List<GitWorkingTreesListEntry>.toBranchKeys(): Set<GitWorktreeBranchKey> =
    filterIsInstance<GitWorktreeRow>().mapNotNull { it.resolveReviewBranchKey() }.toSet()

  /**
   * Starts a lookup [Job] for every repository whose branch set in [keys] is new or has changed, and cancels and
   * removes the lookup for every repository that dropped out of [keys] entirely, leaving a repository's lookup
   * untouched when its branch set did not change - so an unrelated repository's worktree appearing or disappearing
   * never re-triggers, or discards the result of, another repository's PR/MR lookup. One [GitBranchReviewPresenter]
   * subscription now covers every branch of a repository at once, so a hosting service can batch the lookup instead
   * of running one call per branch. Each lookup is seeded with a `null` review per branch before its first real
   * emission, so a newly added row's icon does not stay simply absent from the map while its lookup is in flight.
   */
  private fun reconcileReviewJobs(keys: Set<GitWorktreeBranchKey>) {
    val newBranchesByRepository = keys.groupBy({ it.repositoryId }, { it.branch }).mapValues { (_, branches) -> branches.toSet() }

    val removedRepositoryIds = reviewBranchesByRepository.keys - newBranchesByRepository.keys
    for (repositoryId in removedRepositoryIds) {
      reviewJobs.remove(repositoryId)?.cancel()
      val removedKeys = reviewBranchesByRepository.getValue(repositoryId).map { GitWorktreeBranchKey(repositoryId, it) }
      _reviews.update { it - removedKeys }
    }

    for ((repositoryId, branches) in newBranchesByRepository) {
      if (reviewBranchesByRepository[repositoryId] == branches) continue

      reviewJobs.remove(repositoryId)?.cancel()
      val droppedKeys = (reviewBranchesByRepository[repositoryId].orEmpty() - branches).map { GitWorktreeBranchKey(repositoryId, it) }
      _reviews.update { it - droppedKeys }

      val repository = GitRepositoryIdCache.getInstance(project).get(repositoryId) ?: continue
      reviewJobs[repositoryId] = cs.launch {
        GitBranchReviewPresenter.getReviewsFlow(repository, branches)
          .onStart { emit(branches.associateWith { null }) }
          .collect { reviewsByBranch ->
            _reviews.update { it + reviewsByBranch.mapKeys { (branch, _) -> GitWorktreeBranchKey(repositoryId, branch) } }
          }
      }
    }

    reviewBranchesByRepository = newBranchesByRepository
  }
}

internal data class GitWorktreeBranchKey(val repositoryId: RepositoryId, val branch: GitStandardLocalBranch)

internal fun GitWorktreeRow.resolveReviewBranchKey(): GitWorktreeBranchKey? =
  gitWorkingTree.currentBranch?.let { GitWorktreeBranchKey(repository.repositoryId, it) }
