// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsSafe
import git4idea.GitStandardLocalBranch
import git4idea.repo.GitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.annotations.ApiStatus

/**
 * Supplies a "this branch has an open pull/merge request" review for an arbitrary set of local branches of a
 * repository, not only its current branch. Used to show a review-status icon on branches checked out in a
 * linked worktree.
 */
@ApiStatus.Experimental
interface GitBranchReviewPresenter {
  companion object {
    private val LOG = thisLogger()

    private val EP_NAME = ExtensionPointName<GitBranchReviewPresenter>("Git4Idea.gitBranchReviewPresenter")

    /**
     * A single extension's lookup failing (for example an HTTP error from a hosting service) must not stop
     * every other branch's review from ever updating again, so this is the one place that contains it -
     * no extension needs its own error handling.
     */
    fun getReviewsFlow(repository: GitRepository, branches: Set<GitStandardLocalBranch>): Flow<Map<GitStandardLocalBranch, Review?>> {
      if (branches.isEmpty()) return flowOf(emptyMap())
      return (EP_NAME.computeSafeIfAny { it.getReviewsFlow(repository, branches) } ?: flowOf(branches.associateWith { null }))
        .catch { c ->
          rethrowControlFlowException(c)
          LOG.warn("Failed to compute reviews for branches ${branches.joinToString { it.name }} in ${repository.root}", c)
          emit(branches.associateWith { null })
        }
    }
  }

  /**
   * @return a flow of the reviews for [branches] in [repository], mapping every one of [branches] to its review or
   * to `null` once this extension determines that branch has no open review, or null (not a flow) when this
   * extension's hosting service does not manage [repository] at all, so the caller can try the next extension.
   */
  fun getReviewsFlow(repository: GitRepository, branches: Set<GitStandardLocalBranch>): Flow<Map<GitStandardLocalBranch, Review?>>?

  /**
   * [equals] and [hashCode] deliberately ignore [onOpen]: a lambda compares by reference, so including it would
   * make two reviews with the same [title] compare unequal whenever they are built by separate lookups, defeating
   * [kotlinx.coroutines.flow.StateFlow]'s de-duplication.
   */
  class Review(
    val title: @NlsSafe String,
    private val onOpen: () -> Unit,
  ) {
    /** Opens the review in its hosting plugin's own UI. */
    fun open(): Unit = onOpen()

    override fun equals(other: Any?): Boolean = other is Review && title == other.title

    override fun hashCode(): Int = title.hashCode()
  }
}
