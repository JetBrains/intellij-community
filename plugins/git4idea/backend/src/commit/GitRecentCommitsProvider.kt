// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.coroutineToIndicator
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
import git4idea.repo.GitRepoInfo
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.repo.GitRepositoryStateChangeListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import kotlin.collections.emptyList
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTimedValue

/**
 * A caching loader for recent commits in Git repositories
 *
 * @param limit The maximum number of commits to retrieve per repository
 * @param userScope Specifies whether to filter commits by the user
 * @param stopAtFirstMergeCommit If true, only return commits from HEAD until (but not including) the first merge commit
 * @param unpublishedOnly If true, only return commits that haven't been pushed to a protected remote branch
 * @param preload If true, preload cache entries for existing and changed/created repositories
 */
@ApiStatus.Experimental
class GitRecentCommitsProvider(
  private val project: Project,
  private val scope: CoroutineScope,
  private val limit: Int,
  private val userScope: UserScope = UserScope.CURRENT_USER,
  private val stopAtFirstMergeCommit: Boolean = false,
  private val unpublishedOnly: Boolean = false,
  private val preload: Boolean = false,
) {
  enum class UserScope {
    CURRENT_USER,
    ALL_USERS,
  }

  private val limitedDispatcher = Dispatchers.IO.limitedParallelism(1)

  private val cache: Cache<VirtualFile, Deferred<List<VcsCommitMetadata>>> = Caffeine.newBuilder()
    .maximumSize(MAX_REPOSITORIES.toLong())
    .removalListener { _: VirtualFile?, deferred: Deferred<List<VcsCommitMetadata>>?, _ ->
      deferred?.cancel()
    }
    .executor(Dispatchers.Default.asExecutor())
    .build()

  init {
    project.messageBus.connect(scope).subscribe(GitRepository.GIT_REPO_STATE_CHANGE, object : GitRepositoryStateChangeListener {
      override fun repositoryCreated(repository: GitRepository, info: GitRepoInfo) {
        if (preload && cache.estimatedSize() < MAX_REPOSITORIES) {
          cache.put(repository.root, scheduleRefresh(repository.root))
        }
      }

      override fun repositoryChanged(
        repository: GitRepository,
        previousInfo: GitRepoInfo,
        info: GitRepoInfo,
      ) {
        LOG.debug { "Repository changed, handling cache entry for ${repository.root}" }
        if (preload) {
          cache.put(repository.root, scheduleRefresh(repository.root))
        }
        else {
          cache.invalidate(repository.root)
        }
      }
    })

    if (preload) {
      val repositories = GitRepositoryManager.getInstance(project).repositories
      repositories.take(MAX_REPOSITORIES).forEach {
        cache.put(it.root, scheduleRefresh(it.root))
      }
    }
  }

  suspend fun getRecentCommits(root: VirtualFile): List<VcsCommitMetadata> {
    while (true) {
      check(scope.isActive) { "Provider's scope got cancelled" }
      val deferred = cache.get(root) { scheduleRefresh(root, immediate = true) }

      try {
        return deferred.await()
      }
      catch (@Suppress("IncorrectCancellationExceptionHandling") _: CancellationException) {
        checkCanceled()
        // refresh canceled by removal from the cache, retry
      }
      catch (e: Exception) {
        cache.asMap().remove(root, deferred)
        throw e
      }
    }
  }

  private fun scheduleRefresh(root: VirtualFile, immediate: Boolean = false): Deferred<List<VcsCommitMetadata>> {
    return scope.async {
      if (!immediate) delay(200.milliseconds)
      readRecentCommits(root)
    }
  }

  private suspend fun readRecentCommits(root: VirtualFile): List<VcsCommitMetadata> = withContext(limitedDispatcher) {
    LOG.debug("Reading recent commits for $root (limit: $limit, userScope: $userScope, stopAtFirstMergeCommit: $stopAtFirstMergeCommit, filterNotPublished: $unpublishedOnly)")

    val commits = coroutineToIndicator { loadCommits(root) }

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

  private suspend fun keepOnlyUnpublished(root: VirtualFile, commits: List<VcsCommitMetadata>): List<VcsCommitMetadata> {
    val repository = GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(root) ?: return commits

    // If a commit is published, then all its parents as well.
    // So we can find this published suffix using binary search.
    var low = 0
    var high = commits.size
    while (low < high) {
      val mid = (low + high) / 2
      if (isCommitPublished(repository, commits[mid].id)) high = mid else low = mid + 1
    }
    return commits.take(low)
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
    private const val MAX_REPOSITORIES = 10
  }
}