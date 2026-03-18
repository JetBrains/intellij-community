// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogFilter
import com.intellij.vcs.log.VcsLogRangeFilter
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject.fromParentCount
import git4idea.GitUserRegistry
import git4idea.GitUtil
import git4idea.isCommitPublished
import git4idea.history.GitLogUtil
import git4idea.log.GitLogProvider
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import git4idea.util.CaffeineUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import kotlin.time.measureTimedValue

@ApiStatus.Experimental
class GitRecentCommitsProvider(
  private val project: Project,
  private val scope: CoroutineScope,
  private val limit: Int,
  private val userScope: UserScope = UserScope.CURRENT_USER,
  private val stopAtFirstMergeCommit: Boolean = false, // If true, only return commits from HEAD until (but not including) the first merge commit
  private val unpublishedOnly: Boolean = false, // If true, only return commits that haven't been pushed to a protected remote branch
) {
  enum class UserScope {
    CURRENT_USER,
    ALL_USERS,
  }

  private val cache: AsyncLoadingCache<VirtualFile, List<VcsCommitMetadata>> = CaffeineUtil.withIoExecutor()
    .buildAsync { root, _ ->
      scope.future {
        readRecentCommits(root)
      }
    }

  init {
    project.messageBus.connect(scope).subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener { repository ->
      LOG.debug { "Repository changed, refreshing cache entry for ${repository.root}" }
      cache.synchronous().refresh(repository.root)
    })
    cache.synchronous().refreshAll(GitRepositoryManager.getInstance(project).repositories.map { it.root })
  }

  suspend fun getRecentCommits(root: VirtualFile): List<VcsCommitMetadata> {
    return cache.get(root).await()
  }

  private suspend fun readRecentCommits(root: VirtualFile): List<VcsCommitMetadata> = withContext(Dispatchers.IO) {
    LOG.debug("Reading recent commits for $root (limit: $limit, userScope: $userScope, stopAtFirstMergeCommit: $stopAtFirstMergeCommit, filterNotPublished: $unpublishedOnly)")

    val commits = loadCommits(root)

    LOG.debug { "Loaded ${commits.size} commits for $root" }

    if (unpublishedOnly) keepOnlyUnpublished(root, commits) else commits
  }

  private fun loadCommits(root: VirtualFile): List<VcsCommitMetadata> {
    val range = if (stopAtFirstMergeCommit) findFromFirstMergeCommitRange(root) else null

    val filters = VcsLogFilterObject.collection(
      VcsLogFilterObject.fromBranch(GitUtil.HEAD),
      getUserFilter(root)
    )

    val parameters = GitLogProvider.getGitLogParameters(project, root, filters, range, PermanentGraph.Options.Default, limit)
                     ?: return emptyList()

    val (commits, duration) = measureTimedValue {
      GitLogUtil.collectMetadata(project, root, parameters).commits
    }

    LOG.debug { "Git log completed for $root in $duration" }

    return commits
  }

  private fun getUserFilter(root: VirtualFile): VcsLogFilter? {
    return when (userScope) {
      UserScope.ALL_USERS -> null
      UserScope.CURRENT_USER -> {
        val currentUser = GitUserRegistry.getInstance(project).getOrReadUser(root) ?: return null
        VcsLogFilterObject.fromUser(currentUser)
      }
    }
  }

  private fun keepOnlyUnpublished(root: VirtualFile, commits: List<VcsCommitMetadata>): List<VcsCommitMetadata> {
    val repository = GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(root) ?: return commits

    // If a commit is published, then all its parents as well.
    // So we can find this published suffix using binary search
    val firstPublishedCommitIndex = commits.binarySearch { commit ->
      if (isCommitPublished(repository, commit.id)) 1 else -1
    }.let { -it - 1 }

    return commits.take(firstPublishedCommitIndex)
  }

  private fun findFromFirstMergeCommitRange(root: VirtualFile): VcsLogRangeFilter.RefRange? {
    val filters = VcsLogFilterObject.collection(
      VcsLogFilterObject.fromBranch(GitUtil.HEAD),
      fromParentCount(minParents = 2)
    )
    val parameters =
      GitLogProvider.getGitLogParameters(project, root, filters, null, PermanentGraph.Options.Default, 1)
      ?: return null

    val mergeCommitHash = GitLogUtil.collectMetadata(project, root, parameters).commits.firstOrNull()?.id?.asString()
                          ?: return null
    return VcsLogRangeFilter.RefRange(mergeCommitHash, GitUtil.HEAD)
  }

  companion object {
    private val LOG = logger<GitRecentCommitsProvider>()
  }
}