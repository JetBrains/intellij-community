// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import git4idea.GitUserRegistry
import git4idea.GitUtil
import git4idea.history.GitLogUtil
import git4idea.log.GitLogProvider
import git4idea.repo.GitRepoInfo
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryStateChangeListener
import git4idea.util.CaffeineUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.measureTimedValue

@ApiStatus.Experimental
@Service(Service.Level.PROJECT)
class GitMyRecentCommitsProvider(private val project: Project, private val scope: CoroutineScope) {
  private val requestedDepth = ConcurrentHashMap<VirtualFile, Int>()

  private val cache: AsyncLoadingCache<VirtualFile, List<VcsCommitMetadata>> = CaffeineUtil.withIoExecutor()
    .buildAsync { root, executor ->
      val commitsToLoad = requestedDepth.get(root) ?: return@buildAsync CompletableFuture.completedFuture(emptyList())
      scope.future {
        readRecentCommits(root, commitsToLoad)
      }
    }

  init {
    project.messageBus.connect(scope)
      .subscribe(GitRepository.GIT_REPO_STATE_CHANGE, object : GitRepositoryStateChangeListener {
        override fun repositoryChanged(repository: GitRepository, previousInfo: GitRepoInfo, info: GitRepoInfo) {
          LOG.debug { "Refreshing cache entry for ${repository.root}" }
          cache.synchronous().refresh(repository.root)
        }
      })
  }

  suspend fun getRecentCommits(root: VirtualFile, limit: Int): List<VcsCommitMetadata> {
    var shouldInvalidate = false
    requestedDepth.compute(root) { _, current ->
      if (current == null || current < limit) {
        shouldInvalidate = true
        limit
      }
      else current
    }
    if (shouldInvalidate) {
      LOG.debug { "Limit updated for $root - $limit" }
      cache.synchronous().invalidate(root)
    }
    return cache.get(root).await().take(limit)
  }

  @RequiresBackgroundThread
  private fun readRecentCommits(root: VirtualFile, limit: Int): List<VcsCommitMetadata> {
    LOG.debug("Reading recent commits for $root")

    val currentUser = GitUserRegistry.getInstance(project).getOrReadUser(root) ?: return emptyList()
    val filters = VcsLogFilterObject.collection(VcsLogFilterObject.fromBranch(GitUtil.HEAD), VcsLogFilterObject.fromUser(currentUser))
    val parameters =
      GitLogProvider.getGitLogParameters(project, root, filters, null, PermanentGraph.Options.Default, limit)
      ?: return emptyList()

    val (commits, duration) = measureTimedValue {
      GitLogUtil.collectMetadata(project, root, parameters).commits
    }

    LOG.debug { "Loaded ${commits.size} commits for $root in ${duration}ms" }

    return commits
  }

  companion object {
    private val LOG = thisLogger()

    fun getInstance(project: Project): GitMyRecentCommitsProvider = project.service()
  }
}